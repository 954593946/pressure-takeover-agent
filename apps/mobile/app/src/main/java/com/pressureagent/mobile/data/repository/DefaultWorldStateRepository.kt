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
 * 4. If SSE drops mid-stream, polling restarts; SSE reconnects in background.
 * 5. Only accept monotonically increasing revisions; discard stale or duplicate frames.
 * 6. Session changes clear local cache and force a fresh snapshot.
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

    // Stale threshold: 45s gives Render cold-start enough room
    private val staleThresholdMs = 45_000L

    private fun updateConnectionStatus() {
        val now = System.currentTimeMillis()
        val stale = lastDataTimestamp > 0 && now - lastDataTimestamp > staleThresholdMs
        _connectionStatus.value = when {
            sseActive -> ConnectionStatus.CONNECTED
            pollingActive && stale -> ConnectionStatus.DISCONNECTED
            pollingActive -> ConnectionStatus.POLLING
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

    private fun connectSse() {
        scope.launch {
            // Wrap SSE observation to auto-restart polling on drop
            while (isActive) {
                try {
                    sseClient.observe().collect { state ->
                        sseActive = true
                        applyState(state)
                    }
                } catch (_: Exception) {
                    AppLogger.w("Repo", "SSE stream dropped, polling will cover")
                }
                sseActive = false
                updateConnectionStatus()
                // Wait before SSE reconnect attempt (polling handles the gap)
                delay(5_000L)
            }
        }
    }

    private fun startPollingAsFallback() {
        pollingJob = scope.launch {
            delay(3_000L)
            while (isActive) {
                if (!sseActive) {
                    pollingActive = true
                    try {
                        val state = api.getWorldState()
                        applyState(state)
                    } catch (e: Exception) {
                        handleApiError(e)
                    }
                    updateConnectionStatus()
                } else {
                    pollingActive = false
                }
                delay(pollingIntervalMs)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
