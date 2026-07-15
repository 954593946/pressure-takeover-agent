package com.pressureagent.mobile.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventSource
import com.pressureagent.mobile.domain.model.EventType
import com.pressureagent.mobile.domain.model.Priority
import com.pressureagent.mobile.domain.model.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class CreateTaskUiState(
    val title: String = "",
    val location: String = "",
    val scheduledAtIso: String? = null,
    val scheduledAtDisplay: String = "",
    val taskType: TaskType = TaskType.RIGID,
    val priority: Priority = Priority.MEDIUM,
    val waitingParties: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val titleError: String? = null,
    val submitSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTaskUiState())
    val uiState: StateFlow<CreateTaskUiState> = _uiState.asStateFlow()

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, titleError = null) }
    }

    fun onLocationChange(location: String) {
        _uiState.update { it.copy(location = location) }
    }

    fun onScheduledAtSelected(iso: String, display: String) {
        _uiState.update { it.copy(scheduledAtIso = iso, scheduledAtDisplay = display) }
    }

    fun onTaskTypeSelected(type: TaskType) {
        _uiState.update { it.copy(taskType = type) }
    }

    fun onPrioritySelected(priority: Priority) {
        _uiState.update { it.copy(priority = priority) }
    }

    fun onWaitingPartyToggle(party: String) {
        _uiState.update { state ->
            val current = state.waitingParties
            state.copy(
                waitingParties = if (current.contains(party)) current - party else current + party
            )
        }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "请输入任务标题") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val payload = buildJsonObject {
                    put("title", state.title.trim())
                    if (state.location.isNotBlank()) put("location", state.location.trim())
                    if (state.scheduledAtIso != null) put("scheduledAt", state.scheduledAtIso)
                    put("type", state.taskType.name.lowercase())
                    put("priority", state.priority.name.lowercase())
                    if (state.waitingParties.isNotEmpty()) {
                        put("waitingParties", buildJsonArray {
                            state.waitingParties.forEach { add(JsonPrimitive(it)) }
                        })
                    }
                }
                val event = Event(
                    eventId = UUID.randomUUID().toString(),
                    type = EventType.TASK_INPUT_RECEIVED,
                    source = EventSource.MOBILE,
                    occurredAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    payload = payload,
                )
                repository.submitEvent(event)
                _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }
    fun onNavigatedAfterSuccess() { _uiState.update { it.copy(submitSuccess = false) } }
}
