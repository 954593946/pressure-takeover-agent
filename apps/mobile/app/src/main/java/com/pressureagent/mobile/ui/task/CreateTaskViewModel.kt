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

enum class SyncStatus { IDLE, SYNCING, SYNCED, FAILED }

data class CreateTaskUiState(
    val quickTitle: String = "",
    val quickTimeIso: String = "",
    val quickTimeDisplay: String = "",
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val error: String? = null,
)

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val localTasks: LocalTaskStore,
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTaskUiState())
    val uiState: StateFlow<CreateTaskUiState> = _uiState.asStateFlow()

    private var currentSessionId: String = ""

    init {
        // Observe WorldState to keep current sessionId
        viewModelScope.launch {
            try {
                repository.worldState.collect { ws ->
                    if (ws.sessionId.isNotBlank()) {
                        currentSessionId = ws.sessionId
                    }
                }
            } catch (_: Exception) {
                // WorldState collection failed — sessionId may be stale
            }
        }
    }

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

        // 1. Save locally for instant calendar display
        val localId = localTasks.addTask(title, time)

        // 2. Submit to backend with proper session_id
        _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING, error = null) }

        val text = buildString {
            append("创建任务：$title")
            if (time != null) append("，时间：$time")
        }

        viewModelScope.launch {
            try {
                repository.submitEvent(
                    Event(
                        eventId = UUID.randomUUID().toString(),
                        sessionId = currentSessionId,
                        type = EventType.TASK_CREATED,
                        source = EventSource.MOBILE,
                        timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        payload = buildJsonObject {
                            put("title", title)
                            if (time != null) put("scheduled_at", time)
                            put("task_type", "flexible")
                        },
                    )
                )
                // Backend accepted — remove local copy since WorldState will provide the authoritative task
                localTasks.removeTask(localId)
                _uiState.update { it.copy(syncStatus = SyncStatus.SYNCED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(syncStatus = SyncStatus.FAILED, error = "同步失败: ${e.message}") }
            }
        }
    }

    /** Retry a previously failed sync — reuses event payload */
    fun retrySync() {
        val title = _uiState.value.quickTitle.trim()
        if (title.isBlank()) return
        val time = _uiState.value.quickTimeIso.ifBlank { null }

        _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING, error = null) }

        viewModelScope.launch {
            try {
                val localId = localTasks.addTask(title, time)
                repository.submitEvent(
                    Event(
                        eventId = UUID.randomUUID().toString(),
                        sessionId = currentSessionId,
                        type = EventType.TASK_CREATED,
                        source = EventSource.MOBILE,
                        timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        payload = buildJsonObject {
                            put("title", title)
                            if (time != null) put("scheduled_at", time)
                            put("task_type", "flexible")
                        },
                    )
                )
                localTasks.removeTask(localId)
                _uiState.update { it.copy(syncStatus = SyncStatus.SYNCED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(syncStatus = SyncStatus.FAILED, error = "重试失败: ${e.message}") }
            }
        }
    }

    fun onNavigatedAfterSuccess() { _uiState.update { it.copy(syncStatus = SyncStatus.IDLE) } }

    fun dismissError() { _uiState.update { it.copy(error = null) } }
}
