package com.pressureagent.mobile.data.repository

import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventResponse
import com.pressureagent.mobile.domain.model.WorldState
import kotlinx.coroutines.flow.Flow

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

    /** Fetch the latest snapshot once. */
    suspend fun refresh(): WorldState

    /** Submit a user event. */
    suspend fun submitEvent(event: Event): EventResponse
}
