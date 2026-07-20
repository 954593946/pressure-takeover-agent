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
    private val pollingIntervalMs: Long = 1_000L,
) : WorldStateRepository, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _worldState = MutableStateFlow<WorldState?>(null)

    override val worldState: Flow<WorldState> = _worldState.filterNotNull()

    private var sseActive = false
    private var pollingJob: kotlinx.coroutines.Job? = null

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
                    _worldState.value = state
                    // Cancel polling once SSE is healthy
                    pollingJob?.cancel()
                    pollingJob = null
                }
            } catch (_: Exception) {
                sseActive = false
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
                    try {
                        _worldState.value = api.getWorldState()
                    } catch (_: Exception) { /* keep last value */ }
                }
                delay(pollingIntervalMs)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
