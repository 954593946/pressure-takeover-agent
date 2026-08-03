package com.pressureagent.mobile.ui.task

import com.pressureagent.mobile.data.repository.ConnectionStatus
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventResponse
import com.pressureagent.mobile.domain.model.WorldState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTaskViewModelTest {

    @Test
    fun failedRetryReusesEventIdAndAuthoritativeNaturalLanguagePayload() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingRepository(failuresBeforeSuccess = 1)
            val viewModel = CreateTaskViewModel(repository)
            val scheduledAt = "2026-08-03T18:10:00+08:00"
            runCurrent()

            viewModel.onQuickTitleChange("接孩子")
            viewModel.onQuickTimeSelected(scheduledAt, "08月03日 18:10")
            viewModel.onQuickCreate()
            advanceUntilIdle()

            assertEquals(SyncStatus.FAILED, viewModel.uiState.value.syncStatus)
            assertEquals(1, repository.submittedEvents.size)
            val firstEvent = repository.submittedEvents.single()
            val payload = firstEvent.payload
            assertTrue(payload.getValue("text").jsonPrimitive.content.contains("接孩子"))
            assertTrue(payload.getValue("text").jsonPrimitive.content.contains(scheduledAt))
            assertTrue("tasks" !in payload)

            viewModel.retrySync()
            advanceUntilIdle()

            assertEquals(SyncStatus.SYNCED, viewModel.uiState.value.syncStatus)
            assertEquals(2, repository.submittedEvents.size)
            val retriedEvent = repository.submittedEvents.last()
            assertEquals(firstEvent.eventId, retriedEvent.eventId)
            assertEquals(firstEvent.timestamp, retriedEvent.timestamp)
            assertEquals(firstEvent.payload, retriedEvent.payload)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exposesSyncingUntilAuthoritativeBackendAcceptsEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gate = CompletableDeferred<Unit>()
            val repository = RecordingRepository(submitGate = gate)
            val viewModel = CreateTaskViewModel(repository)
            runCurrent()

            viewModel.onQuickTitleChange("准备分享材料")
            viewModel.onQuickCreate()
            runCurrent()

            assertEquals(SyncStatus.SYNCING, viewModel.uiState.value.syncStatus)
            assertEquals(1, repository.submittedEvents.size)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(SyncStatus.SYNCED, viewModel.uiState.value.syncStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun waitsForWorldStateSessionBeforeCreatingEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingRepository(initialSessionId = "")
            val viewModel = CreateTaskViewModel(repository)
            runCurrent()

            viewModel.onQuickTitleChange("准备分享材料")
            viewModel.onQuickCreate()

            assertEquals(SyncStatus.FAILED, viewModel.uiState.value.syncStatus)
            assertEquals(0, repository.submittedEvents.size)

            repository.updateSession("session-ready")
            runCurrent()
            viewModel.retrySync()
            advanceUntilIdle()

            assertEquals(SyncStatus.SYNCED, viewModel.uiState.value.syncStatus)
            assertEquals("session-ready", repository.submittedEvents.single().sessionId)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class RecordingRepository(
    private var failuresBeforeSuccess: Int = 0,
    private val submitGate: CompletableDeferred<Unit>? = null,
    initialSessionId: String = "session-test",
) : WorldStateRepository {
    val submittedEvents = mutableListOf<Event>()
    private val state = MutableStateFlow(WorldState(sessionId = initialSessionId))

    override val worldState: Flow<WorldState> = state
    override val connectionStatus: StateFlow<ConnectionStatus> =
        MutableStateFlow(ConnectionStatus.CONNECTED)

    override suspend fun refresh(): WorldState = error("not used")

    override suspend fun submitEvent(event: Event): EventResponse {
        submittedEvents += event
        submitGate?.await()
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess -= 1
            error("network unavailable")
        }
        return EventResponse(eventId = event.eventId, accepted = true, revision = submittedEvents.size)
    }

    override suspend fun resetSession(): WorldState = error("not used")

    fun updateSession(sessionId: String) {
        state.value = WorldState(sessionId = sessionId)
    }
}
