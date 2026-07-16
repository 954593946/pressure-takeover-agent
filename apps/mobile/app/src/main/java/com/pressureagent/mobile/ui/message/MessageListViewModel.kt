package com.pressureagent.mobile.ui.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class MessageListUiState(
    val messageActions: List<Action> = emptyList(),
    val completedMessages: List<Action> = emptyList(),
    val confirmation: Confirmation? = null,
    val outputConclusion: String = "",
)

@HiltViewModel
class MessageListViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageListUiState())
    val uiState: StateFlow<MessageListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                val messageActions = ws.actions.filter { it.type == ActionType.MESSAGE }
                _uiState.value = MessageListUiState(
                    messageActions = messageActions.filter { it.status != ActionStatus.COMPLETED },
                    completedMessages = messageActions.filter { it.status == ActionStatus.COMPLETED },
                    confirmation = ws.confirmation,
                    outputConclusion = ws.output?.conclusion ?: "",
                )
            }
        }
    }

    fun confirm() {
        val c = _uiState.value.confirmation ?: return
        submitConfirmation(c.confirmationId, "accepted")
    }

    fun reject() {
        val c = _uiState.value.confirmation ?: return
        submitConfirmation(c.confirmationId, "rejected")
    }

    private fun submitConfirmation(confirmationId: String, decision: String) {
        viewModelScope.launch {
            val event = Event(
                eventId = UUID.randomUUID().toString(),
                sessionId = "",
                type = EventType.CONFIRMATION_CONFIRMED,
                source = EventSource.MOBILE,
                timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                payload = buildJsonObject {
                    put("confirmationId", confirmationId)
                    put("decision", decision)
                },
            )
            repository.submitEvent(event)
        }
    }
}
