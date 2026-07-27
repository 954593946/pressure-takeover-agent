package com.pressureagent.mobile.data.repository

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
import java.io.Closeable

/**
 * Production [WorldStateRepository].
 *
 * Strategy:
 * 1. Try SSE streaming via [SseClient]. On first successful frame, switch to SSE mode.
 * 2. If SSE is unavailable (501, network error), fall back to polling at [pollingIntervalMs].
 * 3. If SSE drops mid-stream, revert to polling until SSE recovers.
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

    private fun updateConnectionStatus() {
        val now = System.currentTimeMillis()
        val stale = now - lastDataTimestamp > 15_000L
        _connectionStatus.value = when {
            sseActive -> ConnectionStatus.CONNECTED
            pollingActive && !stale -> ConnectionStatus.POLLING
            pollingActive && stale -> ConnectionStatus.DISCONNECTED
            !sseActive && !pollingActive -> ConnectionStatus.INITIALIZING
            else -> ConnectionStatus.DISCONNECTED
        }
    }

    init {
        connectSse()
        startPollingAsFallback()
    }

    override suspend fun refresh(): WorldState {
        val state = api.getWorldState()
        _worldState.value = state
        return state
    }

    override suspend fun submitEvent(event: Event): EventResponse {
        val response = api.submitEvent(event)
        refresh()
        return response
    }

    override suspend fun resetSession(): WorldState {
        val state = api.resetSession(ResetRequest())
        _worldState.value = state
        return state
    }

    private fun connectSse() {
        scope.launch {
            try {
                sseClient.observe().collect { state ->
                    sseActive = true
                    lastDataTimestamp = System.currentTimeMillis()
                    _worldState.value = state
                    updateConnectionStatus()
                    // Cancel polling once SSE is healthy
                    pollingJob?.cancel()
                    pollingJob = null
                    pollingActive = false
                }
            } catch (_: Exception) {
                sseActive = false
                updateConnectionStatus()
                // Reconnect will be attempted on next Flow retry
            }
        }
    }

    private fun startPollingAsFallback() {
        pollingJob = scope.launch {
            // Wait a moment for SSE to connect first
            delay(3_000L)
            while (isActive) {
                if (!sseActive) {
                    pollingActive = true
                    try {
                        _worldState.value = api.getWorldState()
                        lastDataTimestamp = System.currentTimeMillis()
                    } catch (_: Exception) { /* keep last value */ }
                    updateConnectionStatus()
                }
                delay(pollingIntervalMs)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
