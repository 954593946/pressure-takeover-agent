package com.pressureagent.mobile.data.repository

import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.data.remote.AgentApiService
import com.pressureagent.mobile.data.remote.ResetRequest
import com.pressureagent.mobile.data.remote.SseClient
import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventResponse
import com.pressureagent.mobile.domain.model.WorldState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.Closeable

/**
 * Production [WorldStateRepository].
 *
 * Strategy:
 * 1. Fetch initial snapshot via GET /v1/state to seed the UI immediately.
 * 2. Connect SSE streaming via [SseClient]. On first successful frame, switch to SSE mode.
 * 3. If SSE is unavailable (501, network error), fall back to polling at [pollingIntervalMs].
 * 4. If SSE drops mid-stream, polling covers the gap; SSE reconnects in background.
 * 5. Only accept monotonically increasing revisions; discard stale or duplicate frames.
 * 6. Session changes clear local cache and force a fresh snapshot.
 *
 * Connection status debouncing:
 * - A single polling failure does NOT immediately flip to DISCONNECTED.
 * - We require [disconnectDebounceCount] consecutive failures before reporting DISCONNECTED.
 * - Similarly, the stale threshold is generous (120s) to avoid false positives
 *   when the backend simply has no state updates to send.
 */
class DefaultWorldStateRepository(
    private val api: AgentApiService,
    private val sseClient: SseClient,
    private val pollingIntervalMs: Long = 5_000L,
) : WorldStateRepository, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _worldState = MutableStateFlow<WorldState?>(null)
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.INITIALIZING)

    override val worldState: Flow<WorldState> = _worldState.filterNotNull()
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private var sseActive = false
    private var pollingActive = false
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var lastDataTimestamp = 0L
    private var lastRevision = -1
    private var currentSessionId: String? = null

    // ── Debounce / stale settings ──────────────────────────────────────────
    // Stale threshold: 120s gives plenty of room for backend idle periods.
    // During normal operation, WorldState only changes on events; there may be
    // long stretches with no updates (e.g. steady driving).
    private val staleThresholdMs = 120_000L

    // Number of consecutive polling failures before reporting DISCONNECTED.
    // A single transient failure (network blip, server restart) should not
    // alarm the user.
    private val disconnectDebounceCount = 3
    private var consecutivePollingFailures = 0

    // Track when we last heard from SSE specifically, for hysteresis.
    private var lastSseActivity = 0L

    private fun updateConnectionStatus() {
        val now = System.currentTimeMillis()
        val stale = lastDataTimestamp > 0 && now - lastDataTimestamp > staleThresholdMs

        _connectionStatus.value = when {
            // Auth failure is sticky — don't override it
            _connectionStatus.value == ConnectionStatus.UNAUTHORIZED ->
                ConnectionStatus.UNAUTHORIZED

            sseActive -> {
                // SSE is connected. Even if data is stale, SSE is still the transport.
                // Reset polling failure counter since SSE is healthy.
                consecutivePollingFailures = 0
                ConnectionStatus.CONNECTED
            }

            pollingActive && !stale -> {
                // Polling is working and data is fresh enough
                ConnectionStatus.POLLING
            }

            pollingActive && stale && consecutivePollingFailures >= disconnectDebounceCount -> {
                // Multiple polling failures + data is stale → truly disconnected
                ConnectionStatus.DISCONNECTED
            }

            pollingActive && stale -> {
                // Data is stale but we haven't failed enough times yet — stay in POLLING
                // to avoid a brief flip to DISCONNECTED
                ConnectionStatus.POLLING
            }

            !sseActive && !pollingActive -> ConnectionStatus.INITIALIZING

            else -> ConnectionStatus.DISCONNECTED
        }
    }

    init {
        // Fetch initial snapshot first, then connect SSE
        fetchInitialSnapshot()
        connectSse()
        // Polling runs continuously as fallback — it auto-pauses when SSE is active
        startPollingAsFallback()
    }

    private fun fetchInitialSnapshot() {
        scope.launch {
            try {
                val state = api.getWorldState()
                applyState(state)
                AppLogger.i("Repo", "Initial snapshot fetched: session=${state.sessionId}, revision=${state.revision}")
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    AppLogger.e("Repo", "Initial snapshot: 401 Unauthorized")
                    _connectionStatus.value = ConnectionStatus.UNAUTHORIZED
                } else {
                    AppLogger.w("Repo", "Initial snapshot fetch failed: HTTP ${e.code()}")
                }
            } catch (e: Exception) {
                AppLogger.w("Repo", "Initial snapshot fetch failed, waiting for SSE/polling: ${e.message}")
            }
        }
    }

    private fun handleApiError(e: Exception) {
        if (e is HttpException && e.code() == 401) {
            _connectionStatus.value = ConnectionStatus.UNAUTHORIZED
            AppLogger.e("Repo", "401 Unauthorized — token may be invalid or expired")
        }
    }

    /**
     * Apply a WorldState frame, enforcing monotonic revision and session continuity.
     */
    private fun applyState(state: WorldState) {
        val sessionChanged = currentSessionId != null && state.sessionId.isNotBlank() &&
            state.sessionId != currentSessionId

        if (sessionChanged) {
            AppLogger.i("Repo", "Session changed: $currentSessionId -> ${state.sessionId}, resetting revision tracker")
            lastRevision = -1
        }

        if (state.sessionId.isNotBlank()) {
            currentSessionId = state.sessionId
        }

        // Only accept monotonically increasing revisions (or initial state)
        if (lastRevision >= 0 && state.revision <= lastRevision) {
            AppLogger.d("Repo", "Skipping stale frame: revision=${state.revision} <= last=$lastRevision")
            return
        }

        lastRevision = state.revision
        lastDataTimestamp = System.currentTimeMillis()
        _worldState.value = state
        updateConnectionStatus()
    }

    override suspend fun refresh(): WorldState {
        return try {
            val state = api.getWorldState()
            applyState(state)
            state
        } catch (e: Exception) {
            handleApiError(e)
            throw e
        }
    }

    override suspend fun submitEvent(event: Event): EventResponse {
        val response = api.submitEvent(event)
        AppLogger.i("Repo", "Event submitted: id=${response.eventId}, accepted=${response.accepted}, revision=${response.revision}")
        refresh()
        return response
    }

    override suspend fun resetSession(): WorldState {
        lastRevision = -1
        currentSessionId = null
        val state = api.resetSession(ResetRequest())
        applyState(state)
        return state
    }

    /**
     * Connect to SSE stream.
     *
     * The SSE client handles its own reconnection internally (exponential backoff
     * + jitter). This coroutine simply collects the flow; if the flow ever
     * terminates (which shouldn't happen in normal operation), we restart it
     * after a short delay.
     */
    private fun connectSse() {
        scope.launch {
            while (isActive) {
                try {
                    sseClient.observe().collect { state ->
                        if (!sseActive) {
                            AppLogger.i("Repo", "SSE stream active — switching to real-time mode")
                        }
                        sseActive = true
                        lastSseActivity = System.currentTimeMillis()
                        consecutivePollingFailures = 0
                        applyState(state)
                    }
                } catch (_: Exception) {
                    AppLogger.w("Repo", "SSE stream dropped, polling will cover the gap")
                }
                // SSE flow terminated — mark inactive and wait before reconnecting
                sseActive = false
                updateConnectionStatus()
                if (isActive) {
                    AppLogger.d("Repo", "SSE reconnecting in 5s...")
                    delay(5_000L)
                }
            }
        }
    }

    /**
     * Polling fallback — runs continuously.
     *
     * When SSE is active, polling skips the HTTP call (saves battery/data) but
     * still ticks to monitor the situation. When SSE drops, polling becomes the
     * primary data source and reports its own health via the debounced status.
     */
    private fun startPollingAsFallback() {
        pollingJob = scope.launch {
            // Initial delay gives SSE time to connect first
            delay(2_000L)
            while (isActive) {
                if (!sseActive) {
                    pollingActive = true
                    try {
                        val state = api.getWorldState()
                        applyState(state)
                        // Successful poll → reset failure counter
                        consecutivePollingFailures = 0
                    } catch (e: Exception) {
                        consecutivePollingFailures++
                        handleApiError(e)
                        if (consecutivePollingFailures >= disconnectDebounceCount) {
                            AppLogger.w("Repo", "Polling failed $consecutivePollingFailures times consecutively — marking DISCONNECTED")
                        }
                    }
                    updateConnectionStatus()
                } else {
                    // SSE is active — polling is idle
                    if (pollingActive) {
                        AppLogger.d("Repo", "SSE recovered — polling paused")
                    }
                    pollingActive = false
                    consecutivePollingFailures = 0
                }
                delay(pollingIntervalMs)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
