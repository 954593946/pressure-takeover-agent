package com.pressureagent.mobile.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.local.LocalTaskStore
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

data class CreateTaskUiState(
    val quickTitle: String = "",
    val quickTimeIso: String = "",
    val quickTimeDisplay: String = "",
    val submitSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val localTasks: LocalTaskStore,
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTaskUiState())
    val uiState: StateFlow<CreateTaskUiState> = _uiState.asStateFlow()

    fun onQuickTitleChange(title: String) { _uiState.update { it.copy(quickTitle = title, error = null) } }

    fun onQuickTimeSelected(iso: String, display: String) {
        _uiState.update { it.copy(quickTimeIso = iso, quickTimeDisplay = display) }
    }

    fun onQuickCreate() {
        val title = _uiState.value.quickTitle.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "请输入任务标题") }
            return
        }
        val time = _uiState.value.quickTimeIso.ifBlank { null }

        // 1. Save locally — instant display in calendar
        localTasks.addTask(title, time)

        // 2. Also tell the backend so Agent knows about it (fire-and-forget)
        val text = buildString {
            append("创建任务：$title")
            if (time != null) append("，时间：$time")
        }
        viewModelScope.launch {
            try {
                repository.submitEvent(
                    Event(
                        eventId = UUID.randomUUID().toString(),
                        sessionId = "",
                        type = EventType.USER_UTTERANCE,
                        source = EventSource.MOBILE,
                        timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        payload = buildJsonObject { put("text", text) },
                    )
                )
            } catch (_: Exception) {
                // Backend sync is best-effort; task already saved locally
            }
        }

        _uiState.update { it.copy(submitSuccess = true) }
    }

    fun onNavigatedAfterSuccess() { _uiState.update { it.copy(submitSuccess = false) } }
}
