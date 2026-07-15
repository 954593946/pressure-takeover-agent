package com.pressureagent.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.ConfirmationStatus
import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventSource
import com.pressureagent.mobile.domain.model.EventType
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

data class HomeUiState(
    val stage: com.pressureagent.mobile.domain.model.Stage = com.pressureagent.mobile.domain.model.Stage.IDLE,
    val stageLabel: String = "",
    val agentModeLabel: String = "",
    val tasks: List<com.pressureagent.mobile.domain.model.Task> = emptyList(),
    val risk: com.pressureagent.mobile.domain.model.Risk? = null,
    val vehicle: com.pressureagent.mobile.domain.model.Vehicle? = null,
    val conclusion: String? = null,
    val messages: List<com.pressureagent.mobile.domain.model.Message> = emptyList(),
    val confirmation: com.pressureagent.mobile.domain.model.Confirmation? = null,
    val wearable: com.pressureagent.mobile.domain.model.Wearable? = null,
    val isIdle: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    // ─── Context-aware action ────────────────────────────────────────────────
    val primaryAction: String? = null,      // e.g. "创建演示任务" / "报告延迟" / "请求接管"
    val secondaryAction: String? = null,    // e.g. "重置" (shown when primary is also visible)
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WorldStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeWorldState()
        refresh()
    }

    private fun observeWorldState() {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                val (primary, secondary) = actionsFor(ws.stage, ws.confirmation)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        isIdle = false,
                        stage = ws.stage,
                        stageLabel = stageLabel(ws.stage),
                        agentModeLabel = agentModeLabel(ws.agentMode),
                        tasks = ws.tasks,
                        risk = ws.risk,
                        vehicle = ws.vehicle,
                        conclusion = ws.conclusion,
                        messages = ws.messages,
                        confirmation = ws.confirmation,
                        wearable = ws.wearable,
                        primaryAction = primary,
                        secondaryAction = secondary,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try { repository.refresh() }
            catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun confirm() {
        val c = _uiState.value.confirmation ?: return
        submitEvent(EventType.CONFIRMATION_SUBMITTED, buildJsonObject {
            put("confirmationId", c.id); put("decision", "accepted")
        })
    }

    fun reject() {
        val c = _uiState.value.confirmation ?: return
        submitEvent(EventType.CONFIRMATION_SUBMITTED, buildJsonObject {
            put("confirmationId", c.id); put("decision", "rejected")
        })
    }

    fun onPrimaryAction() {
        val s = _uiState.value.stage
        viewModelScope.launch {
            when (s) {
                com.pressureagent.mobile.domain.model.Stage.IDLE ->
                    submitEvent(EventType.TASK_INPUT_RECEIVED, buildJsonObject { put("action", "create_demo") })
                com.pressureagent.mobile.domain.model.Stage.TASK_CREATED ->
                    submitEvent(EventType.MEETING_DELAY_REPORTED, buildJsonObject { put("delay", true) })
                com.pressureagent.mobile.domain.model.Stage.MEETING_DELAY ->
                    submitEvent(EventType.VEHICLE_MODE_CHANGED, buildJsonObject { put("mode", "driving") })
                com.pressureagent.mobile.domain.model.Stage.DEPARTURE_WARNING ->
                    submitEvent(EventType.VEHICLE_MODE_CHANGED, buildJsonObject { put("mode", "driving") })
                com.pressureagent.mobile.domain.model.Stage.VEHICLE_MODE ->
                    submitEvent(EventType.TRAFFIC_UPDATED, buildJsonObject { put("congestion", true) })
                com.pressureagent.mobile.domain.model.Stage.TRAFFIC_DELAY ->
                    submitEvent(EventType.ASSISTANCE_REQUESTED, buildJsonObject { put("request", "takeover") })
                com.pressureagent.mobile.domain.model.Stage.PRESSURE_TAKEOVER ->
                    submitEvent(EventType.PRESSURE_SIGNAL_REPORTED, buildJsonObject { put("signal", "pending") })
                com.pressureagent.mobile.domain.model.Stage.ROLE_TRANSITION ->
                    submitEvent(EventType.TASK_INPUT_RECEIVED, buildJsonObject { put("action", "continue") })
                else -> { /* WAITING_CONFIRMATION / ACTION_COMPLETED — handled by confirm/reject/reset */ }
            }
        }
    }

    fun onSecondaryAction() {
        submitEvent(EventType.DEMO_RESET_REQUESTED, buildJsonObject { put("reset", true) })
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }

    private fun submitEvent(type: EventType, extra: kotlinx.serialization.json.JsonObject) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val event = Event(
                    eventId = UUID.randomUUID().toString(),
                    type = type,
                    source = EventSource.MOBILE,
                    occurredAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    payload = extra,
                )
                repository.submitEvent(event)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    companion object {
        fun stageLabel(s: com.pressureagent.mobile.domain.model.Stage) = when (s) {
            com.pressureagent.mobile.domain.model.Stage.IDLE -> "待命"
            com.pressureagent.mobile.domain.model.Stage.TASK_CREATED -> "任务已创建"
            com.pressureagent.mobile.domain.model.Stage.MEETING_DELAY -> "会议延迟"
            com.pressureagent.mobile.domain.model.Stage.DEPARTURE_WARNING -> "出发提醒"
            com.pressureagent.mobile.domain.model.Stage.VEHICLE_MODE -> "驾驶中"
            com.pressureagent.mobile.domain.model.Stage.TRAFFIC_DELAY -> "路况拥堵"
            com.pressureagent.mobile.domain.model.Stage.PRESSURE_TAKEOVER -> "Agent 接管中"
            com.pressureagent.mobile.domain.model.Stage.WAITING_CONFIRMATION -> "等待确认"
            com.pressureagent.mobile.domain.model.Stage.ACTION_COMPLETED -> "已完成"
            com.pressureagent.mobile.domain.model.Stage.ROLE_TRANSITION -> "角色过渡"
        }
        fun actionsFor(
            s: com.pressureagent.mobile.domain.model.Stage,
            c: com.pressureagent.mobile.domain.model.Confirmation?,
        ): Pair<String?, String?> = when (s) {
            com.pressureagent.mobile.domain.model.Stage.IDLE -> "创建演示任务" to null
            com.pressureagent.mobile.domain.model.Stage.TASK_CREATED -> "报告会议延迟" to null
            com.pressureagent.mobile.domain.model.Stage.MEETING_DELAY -> "我已出发" to null
            com.pressureagent.mobile.domain.model.Stage.DEPARTURE_WARNING -> "我已出发" to null
            com.pressureagent.mobile.domain.model.Stage.VEHICLE_MODE -> "查看路况" to null
            com.pressureagent.mobile.domain.model.Stage.TRAFFIC_DELAY -> "让 Agent 接管" to null
            com.pressureagent.mobile.domain.model.Stage.PRESSURE_TAKEOVER -> "继续处理" to null
            com.pressureagent.mobile.domain.model.Stage.WAITING_CONFIRMATION -> {
                if (c != null && c.status == ConfirmationStatus.PENDING) null to null // confirm/reject instead
                else "继续" to null
            }
            com.pressureagent.mobile.domain.model.Stage.ACTION_COMPLETED -> "重新演示" to null
            com.pressureagent.mobile.domain.model.Stage.ROLE_TRANSITION -> "继续" to null
        }

        fun agentModeLabel(m: com.pressureagent.mobile.domain.model.AgentMode) = when (m) {
            com.pressureagent.mobile.domain.model.AgentMode.QUIET -> "静默"
            com.pressureagent.mobile.domain.model.AgentMode.OBSERVING -> "观察中"
            com.pressureagent.mobile.domain.model.AgentMode.TAKING_OVER -> "接管中"
            com.pressureagent.mobile.domain.model.AgentMode.AWAITING_CONFIRMATION -> "等待确认"
            com.pressureagent.mobile.domain.model.AgentMode.RESOLVED -> "已解决"
        }
    }
}
