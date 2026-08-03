package com.pressureagent.mobile.ui.task

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
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateTaskUiState())
    val uiState: StateFlow<CreateTaskUiState> = _uiState.asStateFlow()

    private var currentSessionId: String = ""
    private var pendingSubmission: Event? = null

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

    fun onQuickTitleChange(title: String) {
        if (_uiState.value.syncStatus == SyncStatus.SYNCING) return
        invalidatePendingSubmission()
        _uiState.update { it.copy(quickTitle = title, error = null) }
    }

    fun onQuickTimeSelected(iso: String, display: String) {
        if (_uiState.value.syncStatus == SyncStatus.SYNCING) return
        invalidatePendingSubmission()
        _uiState.update {
            it.copy(quickTimeIso = iso, quickTimeDisplay = display, error = null)
        }
    }

    fun onQuickCreate() {
        if (_uiState.value.syncStatus == SyncStatus.SYNCING) return

        val title = _uiState.value.quickTitle.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "请输入任务标题") }
            return
        }
        if (currentSessionId.isBlank()) {
            _uiState.update {
                it.copy(syncStatus = SyncStatus.FAILED, error = "Agent 状态尚未同步，请稍后重试")
            }
            return
        }

        val event = pendingSubmission ?: buildTaskCreatedEvent(
            title = title,
            scheduledAt = _uiState.value.quickTimeIso.ifBlank { null },
        ).also { pendingSubmission = it }
        submitPending(event, failurePrefix = "同步失败")
    }

    /** Retry the exact failed event so backend event_id deduplication remains effective. */
    fun retrySync() {
        if (_uiState.value.syncStatus == SyncStatus.SYNCING) return
        val pending = pendingSubmission
        if (pending != null) {
            submitPending(pending, failurePrefix = "重试失败")
        } else {
            onQuickCreate()
        }
    }

    fun onNavigatedAfterSuccess() { _uiState.update { it.copy(syncStatus = SyncStatus.IDLE) } }

    fun dismissError() { _uiState.update { it.copy(error = null) } }

    private fun submitPending(event: Event, failurePrefix: String) {
        _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING, error = null) }
        viewModelScope.launch {
            try {
                val response = repository.submitEvent(event)
                check(response.accepted) { "Agent 未接受任务事件" }
                pendingSubmission = null
                _uiState.update { it.copy(syncStatus = SyncStatus.SYNCED, error = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(syncStatus = SyncStatus.FAILED, error = "$failurePrefix: ${e.message}")
                }
            }
        }
    }

    private fun buildTaskCreatedEvent(title: String, scheduledAt: String?): Event {
        val eventId = UUID.randomUUID().toString()
        val naturalLanguageText = buildString {
            append(title)
            if (scheduledAt != null) append("，计划时间 $scheduledAt")
        }
        return Event(
            eventId = eventId,
            sessionId = currentSessionId,
            type = EventType.TASK_CREATED,
            source = EventSource.MOBILE,
            timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            payload = buildJsonObject {
                put("text", naturalLanguageText)
            },
        )
    }

    private fun invalidatePendingSubmission() {
        pendingSubmission = null
        if (_uiState.value.syncStatus != SyncStatus.SYNCING) {
            _uiState.update { it.copy(syncStatus = SyncStatus.IDLE, error = null) }
        }
    }
}
