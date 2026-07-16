package com.pressureagent.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.domain.voice.VoiceInputEvent
import com.pressureagent.mobile.domain.voice.VoiceInputProvider
import com.pressureagent.mobile.domain.voice.VoiceOutputProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

data class ChatUiState(
    val stage: Stage = Stage.OFF_VEHICLE_IDLE,
    val pressureLevel: PressureLevel = PressureLevel.L0,
    val primarySurface: PrimarySurface = PrimarySurface.MOBILE,
    val stageLabel: String = "",
    val isCompanionMode: Boolean = false,
    // Chat
    val chatMessages: List<ChatItem> = emptyList(),
    // Input
    val isListening: Boolean = false,
    val voiceText: String = "",
    // UI
    val isLoading: Boolean = false,
    val error: String? = null,
    // Confirmation (lifted to top-level for quick access)
    val pendingConfirmation: Confirmation? = null,
    val conclusion: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: WorldStateRepository,
    private val voiceInput: VoiceInputProvider,
    private val voiceOutput: VoiceOutputProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var lastStage: Stage = Stage.OFF_VEHICLE_IDLE
    private var lastConclusion: String = ""
    private var lastRiskLevel: PressureLevel = PressureLevel.L0
    private var lastTaskIds: Set<String> = emptySet()
    private var lastActionIds: Set<String> = emptySet()
    private var lastOrderIds: Set<String> = emptySet()
    private var lastConfirmationId: String? = null
    private var voiceJob: Job? = null

    init {
        observeWorldState()
        refresh()
    }

    private fun observeWorldState() {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                val newChats = mutableListOf<ChatItem>()

                // 1) Stage change → AURI message
                if (ws.stage != lastStage) {
                    val msg = stageMessage(ws)
                    if (msg != null) {
                        newChats.add(ChatItem(id = "stage_${ws.stage.name}_${ws.revision}", text = msg, isUser = false))
                        voiceOutput.speak(msg)
                    }
                    lastStage = ws.stage
                }

                // 2) Conclusion change → AURI message
                val conclusion = ws.output?.conclusion.orEmpty()
                if (conclusion.isNotBlank() && conclusion != lastConclusion) {
                    newChats.add(ChatItem(id = "conclusion_${ws.revision}", text = conclusion, isUser = false))
                    voiceOutput.speak(conclusion)
                    lastConclusion = conclusion
                }

                // 3) Risk level change → rich card
                if (ws.risk.pressureLevel != PressureLevel.L0 && ws.risk.pressureLevel != lastRiskLevel) {
                    newChats.add(
                        ChatItem(
                            id = "risk_${ws.revision}",
                            text = riskSummary(ws.risk),
                            isUser = false,
                            richCard = RichCard.RiskInfo(ws.risk, conclusion, ws.eta),
                        )
                    )
                    lastRiskLevel = ws.risk.pressureLevel
                }

                // 4) New tasks → rich card
                val currentTaskIds = ws.tasks.map { it.taskId }.toSet()
                if (currentTaskIds != lastTaskIds && ws.tasks.isNotEmpty()) {
                    newChats.add(
                        ChatItem(
                            id = "tasks_${ws.revision}",
                            text = "今日任务已更新（${ws.tasks.size} 项）",
                            isUser = false,
                            richCard = RichCard.TaskList(ws.tasks),
                        )
                    )
                    lastTaskIds = currentTaskIds
                }

                // 5) New actions (message drafts) → individual rich cards
                val currentActionIds = ws.actions.map { it.actionId }.toSet()
                val newActions = ws.actions.filter { it.actionId !in lastActionIds && it.type == ActionType.MESSAGE && it.status != ActionStatus.COMPLETED }
                newActions.forEach { action ->
                    newChats.add(
                        ChatItem(
                            id = "action_${action.actionId}",
                            text = "已草拟消息给 ${action.target}",
                            isUser = false,
                            richCard = RichCard.MessageDraft(action),
                        )
                    )
                }
                if (ws.actions.isNotEmpty()) lastActionIds = currentActionIds

                // 6) New service orders → rich cards
                val currentOrderIds = ws.serviceOrders.map { it.previewId }.toSet()
                val newOrders = ws.serviceOrders.filter { it.previewId !in lastOrderIds }
                newOrders.forEach { order ->
                    newChats.add(
                        ChatItem(
                            id = "order_${order.previewId}",
                            text = "服务方案已生成：${order.items.size} 项商品 ¥${"%.1f".format(order.total)}",
                            isUser = false,
                            richCard = RichCard.ServicePlan(order),
                        )
                    )
                }
                if (ws.serviceOrders.isNotEmpty()) lastOrderIds = currentOrderIds

                // 7) Confirmation → rich card
                if (ws.confirmation != null && ws.confirmation!!.confirmationId != lastConfirmationId && ws.confirmation!!.status == ConfirmationStatus.PENDING) {
                    newChats.add(
                        ChatItem(
                            id = "confirm_${ws.confirmation!!.confirmationId}",
                            text = "",
                            isUser = false,
                            richCard = RichCard.ConfirmRequest(ws.confirmation!!.confirmationId, conclusion.ifBlank { "是否确认执行？" }),
                        )
                    )
                    lastConfirmationId = ws.confirmation!!.confirmationId
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        stage = ws.stage,
                        pressureLevel = ws.risk.pressureLevel,
                        primarySurface = ws.primarySurface,
                        stageLabel = stageLabel(ws.stage),
                        isCompanionMode = ws.primarySurface != PrimarySurface.MOBILE,
                        conclusion = conclusion,
                        pendingConfirmation = ws.confirmation,
                        chatMessages = if (newChats.isNotEmpty()) it.chatMessages + newChats else it.chatMessages,
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

    // ─── Voice ─────────────────────────────────────────────────────────────

    fun onVoiceToggle() {
        if (voiceInput.isListening) {
            voiceInput.stop()
            voiceJob?.cancel()
            _uiState.update { it.copy(isListening = false) }
        } else {
            _uiState.update { it.copy(isListening = true) }
            voiceJob = viewModelScope.launch {
                voiceInput.listen().collect { event ->
                    when (event) {
                        is VoiceInputEvent.ListeningStarted -> _uiState.update { it.copy(isListening = true) }
                        is VoiceInputEvent.PartialResult -> _uiState.update { it.copy(voiceText = event.text) }
                        is VoiceInputEvent.FinalResult -> {
                            _uiState.update { it.copy(isListening = false, voiceText = event.text) }
                            addUserChat(event.text)
                            submitUtterance(event.text)
                        }
                        is VoiceInputEvent.NoSpeech -> _uiState.update { it.copy(isListening = false, error = "未检测到语音") }
                        is VoiceInputEvent.Error -> _uiState.update { it.copy(isListening = false, error = event.message) }
                    }
                }
            }
        }
    }

    // ─── Text ──────────────────────────────────────────────────────────────

    fun onTextSubmit(text: String) {
        if (text.isBlank()) return
        addUserChat(text)
        submitUtterance(text)
    }

    private fun addUserChat(text: String) {
        val item = ChatItem(id = UUID.randomUUID().toString(), text = text, isUser = true)
        _uiState.update { it.copy(chatMessages = it.chatMessages + item) }
    }

    private fun submitUtterance(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.submitEvent(
                    Event(
                        eventId = UUID.randomUUID().toString(),
                        sessionId = _uiState.value.pendingConfirmation?.confirmationId ?: "",
                        type = EventType.USER_UTTERANCE,
                        source = EventSource.MOBILE,
                        timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        payload = buildJsonObject { put("text", text) },
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ─── Confirmation ──────────────────────────────────────────────────────

    fun confirm() {
        val c = _uiState.value.pendingConfirmation ?: return
        submitConfirmation(c.confirmationId, "accepted")
    }

    fun reject() {
        val c = _uiState.value.pendingConfirmation ?: return
        submitConfirmation(c.confirmationId, "rejected")
    }

    private fun submitConfirmation(confirmationId: String, decision: String) {
        viewModelScope.launch {
            try {
                repository.submitEvent(
                    Event(
                        eventId = UUID.randomUUID().toString(),
                        sessionId = _uiState.value.pendingConfirmation?.confirmationId ?: "",
                        type = EventType.CONFIRMATION_CONFIRMED,
                        source = EventSource.MOBILE,
                        timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        payload = buildJsonObject {
                            put("confirmationId", confirmationId)
                            put("decision", decision)
                        },
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }

    // ─── Labels ────────────────────────────────────────────────────────────

    companion object {
        fun stageMessage(ws: WorldState): String? = when (ws.stage) {
            Stage.OFF_VEHICLE_IDLE -> null
            Stage.PRE_DEPARTURE_WARNING -> "已创建任务：${ws.tasks.joinToString("、") { it.title }}"
            Stage.HANDOVER_TO_VEHICLE -> "已检测到上车，主控权切换到车机 🚗"
            Stage.VEHICLE_OBSERVATION -> "正在前往${ws.tasks.firstOrNull()?.location ?: "目的地"}，一切正常"
            Stage.TAKEOVER_L2 -> "检测到交通拥堵，启动 L2 协调接管"
            Stage.TAKEOVER_L3 -> "⚠️ 高风险场景，启动 L3 高负荷保护"
            Stage.PLANNING -> "正在规划处理方案…"
            Stage.SERVICE_PREPARED -> "服务方案已就绪"
            Stage.WAITING_CONFIRMATION -> "消息已备好，等待确认"
            Stage.EXECUTING -> "正在执行…"
            Stage.SERVICE_EXECUTED -> "服务已执行完成"
            Stage.ACTION_COMPLETED -> "全部处理完成 ✅"
            Stage.COOLDOWN -> "进入冷却模式，停车后可查看复盘"
            Stage.PARKED_REVIEW -> "已停车，可以查看本次复盘"
            Stage.ERROR -> "⚠️ 出现异常"
        }

        fun stageLabel(s: Stage) = when (s) {
            Stage.OFF_VEHICLE_IDLE -> "待命"
            Stage.PRE_DEPARTURE_WARNING -> "注意时间"
            Stage.HANDOVER_TO_VEHICLE -> "交接车机"
            Stage.VEHICLE_OBSERVATION -> "驾驶中"
            Stage.TAKEOVER_L2 -> "L2 协调接管"
            Stage.TAKEOVER_L3 -> "L3 高负荷保护"
            Stage.PLANNING -> "规划处理"
            Stage.SERVICE_PREPARED -> "方案已就绪"
            Stage.WAITING_CONFIRMATION -> "等待确认"
            Stage.EXECUTING -> "执行中"
            Stage.SERVICE_EXECUTED -> "已执行"
            Stage.ACTION_COMPLETED -> "已完成"
            Stage.COOLDOWN -> "冷却中"
            Stage.PARKED_REVIEW -> "停车复盘"
            Stage.ERROR -> "异常"
        }

        fun riskSummary(r: Risk): String = buildString {
            append("${r.pressureLevel.name} 预警")
            if (r.lateMinutes > 0) append(" · 晚到 ${r.lateMinutes} min")
        }
    }
}
