package com.pressureagent.mobile.data.repository

import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventResponse
import com.pressureagent.mobile.domain.model.WorldState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the agent's World State.
 *
 * Implementations:
 * - [DefaultWorldStateRepository] — production, talking to agent-api via HTTP + SSE.
 * - Mock variant — uses [com.pressureagent.mobile.data.mock.MockAgent] interceptor.
 */
interface WorldStateRepository {

    /** Continuous stream of the latest [WorldState]. Never completes; errors are emitted then recovered. */
    val worldState: Flow<WorldState>

    /** Connection health: SSE → polling → disconnected. */
    val connectionStatus: StateFlow<ConnectionStatus>

    /** Fetch the latest snapshot once. */
    suspend fun refresh(): WorldState

    /** Submit a user event. */
    suspend fun submitEvent(event: Event): EventResponse

    /** Reset session to initial state. */
    suspend fun resetSession(): WorldState
}

enum class ConnectionStatus {
    /** Initial state, haven't received any data yet. */
    INITIALIZING,
    /** SSE streaming active — real-time connection. */
    CONNECTED,
    /** SSE down, falling back to polling. */
    POLLING,
    /** No data received for an extended period. */
    DISCONNECTED,
}
