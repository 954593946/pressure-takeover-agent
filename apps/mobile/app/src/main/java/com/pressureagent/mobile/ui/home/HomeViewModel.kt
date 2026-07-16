package com.pressureagent.mobile.ui.home

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

data class ChatItem(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: String = "",
)

data class HomeUiState(
    // World state
    val stage: Stage = Stage.OFF_VEHICLE_IDLE,
    val scene: Scene = Scene.OFF_VEHICLE,
    val primarySurface: PrimarySurface = PrimarySurface.MOBILE,
    val pressureLevel: PressureLevel = PressureLevel.L0,
    val risk: Risk? = null,
    val tasks: List<Task> = emptyList(),
    val eta: String? = null,
    val actions: List<Action> = emptyList(),
    val confirmation: Confirmation? = null,
    val output: InteractionOutput? = null,
    val serviceOrders: List<ServiceOrder> = emptyList(),
    val wearable: Wearable? = null,
    val actionLedger: List<String> = emptyList(),
    // Chat messages
    val chatMessages: List<ChatItem> = emptyList(),
    // UI state
    val isLoading: Boolean = false,
    val error: String? = null,
    // Voice / input state
    val isListening: Boolean = false,
    val voiceText: String = "",
    val isCompanionMode: Boolean = false,
    // Context actions
    val primaryAction: String? = null,
    val secondaryAction: String? = null,
    // Derived
    val stageLabel: String = "",
    val conclusion: String = "",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WorldStateRepository,
    private val voiceInput: VoiceInputProvider,
    private val voiceOutput: VoiceOutputProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastConclusion: String = ""
    private var lastStage: Stage = Stage.OFF_VEHICLE_IDLE
    private var voiceJob: Job? = null

    init {
        observeWorldState()
        refresh()
    }

    private fun observeWorldState() {
        viewModelScope.launch {
            repository.worldState.collect { ws ->
                val (primary, secondary) = actionsFor(ws)
                val newConclusion = ws.output?.conclusion ?: ""

                // Build chat items for UI updates
                val newChats = mutableListOf<ChatItem>()

                // When stage changes, add a system-like message from AURI
                if (ws.stage != lastStage) {
                    val stageMsg = stageMessage(ws)
                    if (stageMsg != null) {
                        newChats.add(ChatItem(
                            id = "stage_${ws.stage.name}_${ws.revision}",
                            text = stageMsg,
                            isUser = false,
                        ))
                        voiceOutput.speak(stageMsg) // TTS: speak AURI response
                    }
                    lastStage = ws.stage
                }

                // When conclusion changes (new AI response), add as AURI message
                if (newConclusion.isNotBlank() && newConclusion != lastConclusion) {
                    newChats.add(ChatItem(
                        id = "conclusion_${ws.revision}",
                        text = newConclusion,
                        isUser = false,
                    ))
                    voiceOutput.speak(newConclusion) // TTS: speak AURI conclusion
                    lastConclusion = newConclusion
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        stage = ws.stage,
                        scene = ws.scene,
                        primarySurface = ws.primarySurface,
                        pressureLevel = ws.risk.pressureLevel,
                        risk = ws.risk,
                        tasks = ws.tasks,
                        eta = ws.eta,
                        actions = ws.actions,
                        confirmation = ws.confirmation,
                        output = ws.output,
                        serviceOrders = ws.serviceOrders,
                        wearable = ws.wearable,
                        actionLedger = ws.actionLedger,
                        stageLabel = stageLabel(ws.stage),
                        conclusion = newConclusion,
                        isCompanionMode = ws.primarySurface != PrimarySurface.MOBILE,
                        primaryAction = primary,
                        secondaryAction = secondary,
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

    // ─── Voice input ────────────────────────────────────────────────────────

    fun onVoiceToggle() {
        if (voiceInput.isListening) {
            // Currently listening → stop
            voiceInput.stop()
            voiceJob?.cancel()
            _uiState.update { it.copy(isListening = false) }
        } else {
            // Start listening
            _uiState.update { it.copy(isListening = true) }
            voiceJob = viewModelScope.launch {
                voiceInput.listen().collect { event ->
                    when (event) {
                        is VoiceInputEvent.ListeningStarted -> {
                            _uiState.update { it.copy(isListening = true) }
                        }
                        is VoiceInputEvent.PartialResult -> {
                            _uiState.update { it.copy(voiceText = event.text) }
                        }
                        is VoiceInputEvent.FinalResult -> {
                            _uiState.update { it.copy(isListening = false, voiceText = event.text) }
                            addUserChat(event.text)
                            submitUtterance(event.text)
                        }
                        is VoiceInputEvent.NoSpeech -> {
                            _uiState.update { it.copy(isListening = false, error = "未检测到语音") }
                        }
                        is VoiceInputEvent.Error -> {
                            _uiState.update { it.copy(isListening = false, error = event.message) }
                        }
                    }
                }
            }
        }
    }

    fun onVoiceResult(text: String) {
        _uiState.update { it.copy(isListening = false, voiceText = text) }
        if (text.isNotBlank()) {
            addUserChat(text)
            submitUtterance(text)
        }
    }

    private fun addUserChat(text: String) {
        val item = ChatItem(id = UUID.randomUUID().toString(), text = text, isUser = true)
        _uiState.update { it.copy(chatMessages = it.chatMessages + item) }
    }

    private fun submitUtterance(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val event = Event(
                    eventId = UUID.randomUUID().toString(),
                    sessionId = _uiState.value.output?.messageId ?: "",
                    type = EventType.USER_UTTERANCE,
                    source = EventSource.MOBILE,
                    timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    payload = buildJsonObject { put("text", text) },
                )
                repository.submitEvent(event)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ─── Text input ─────────────────────────────────────────────────────────

    fun onTextSubmit(text: String) {
        if (text.isBlank()) return
        addUserChat(text)
        submitUtterance(text)
    }

    // ─── Confirmation ───────────────────────────────────────────────────────

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
            try {
                val event = Event(
                    eventId = UUID.randomUUID().toString(),
                    sessionId = _uiState.value.output?.messageId ?: "",
                    type = EventType.CONFIRMATION_CONFIRMED,
                    source = EventSource.MOBILE,
                    timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    payload = buildJsonObject {
                        put("confirmationId", confirmationId)
                        put("decision", decision)
                    },
                )
                repository.submitEvent(event)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ─── Demo story actions ─────────────────────────────────────────────────

    fun onPrimaryAction() {
        val s = _uiState.value.stage
        viewModelScope.launch {
            when (s) {
                Stage.OFF_VEHICLE_IDLE ->
                    submitDemoEvent(EventType.TASK_CREATED, buildJsonObject { put("action", "create_demo") })
                Stage.PRE_DEPARTURE_WARNING ->
                    submitDemoEvent(EventType.MEETING_OVERRUN, buildJsonObject { put("overrun", true) })
                Stage.HANDOVER_TO_VEHICLE ->
                    submitDemoEvent(EventType.SCENE_VEHICLE_ENTERED, buildJsonObject { put("mode", "driving") })
                Stage.VEHICLE_OBSERVATION ->
                    submitDemoEvent(EventType.TRAFFIC_UPDATED, buildJsonObject { put("congestion", true) })
                Stage.TAKEOVER_L2 ->
                    submitDemoEvent(EventType.USER_UTTERANCE, buildJsonObject { put("text", "能不能帮我处理一下") })
                else -> { /* handled by confirm/reject */ }
            }
        }
    }

    fun onSecondaryAction() {
        lastConclusion = ""
        lastStage = Stage.OFF_VEHICLE_IDLE
        _uiState.update { it.copy(chatMessages = emptyList()) }
        submitDemoEvent(EventType.SESSION_RESET, buildJsonObject { put("reset", true) })
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }

    private fun submitDemoEvent(type: EventType, extra: kotlinx.serialization.json.JsonObject) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val event = Event(
                    eventId = UUID.randomUUID().toString(),
                    sessionId = _uiState.value.output?.messageId ?: "",
                    type = type,
                    source = EventSource.MOBILE,
                    timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    payload = extra,
                )
                repository.submitEvent(event)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ─── Labels ─────────────────────────────────────────────────────────────

    companion object {
        fun stageMessage(ws: WorldState): String? = when (ws.stage) {
            Stage.OFF_VEHICLE_IDLE -> null
            Stage.PRE_DEPARTURE_WARNING -> "已创建任务：${ws.tasks.joinToString("、") { it.title }}"
            Stage.HANDOVER_TO_VEHICLE -> "已检测到上车，主控权切换到车机 🚗"
            Stage.VEHICLE_OBSERVATION -> "正在前往${ws.tasks.firstOrNull()?.location ?: "目的地"}，一切正常"
            Stage.TAKEOVER_L2 -> "检测到交通拥堵，ETA 推迟至 ${ws.eta?.let { formatTime(it) } ?: "—"}，启动 L2 协调接管"
            Stage.TAKEOVER_L3 -> "⚠️ 高风险场景，启动 L3 高负荷保护"
            Stage.PLANNING -> "正在规划处理方案…"
            Stage.SERVICE_PREPARED -> "服务方案已就绪，请查看"
            Stage.WAITING_CONFIRMATION -> "消息已备好，等待你的确认"
            Stage.EXECUTING -> "正在执行…"
            Stage.SERVICE_EXECUTED -> "服务已执行完成"
            Stage.ACTION_COMPLETED -> "全部处理完成 ✅"
            Stage.COOLDOWN -> "进入冷却模式，停车后可查看复盘"
            Stage.PARKED_REVIEW -> "已停车，可以查看本次复盘"
            Stage.ERROR -> "⚠️ 出现异常，请重试"
        }

        fun formatTime(iso: String): String = try {
            iso.split("T").getOrNull(1)?.substring(0, 5) ?: iso
        } catch (_: Exception) { iso }

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

        fun actionsFor(ws: WorldState): Pair<String?, String?> = when (ws.stage) {
            Stage.OFF_VEHICLE_IDLE -> "创建演示任务" to null
            Stage.PRE_DEPARTURE_WARNING -> "报告会议延迟" to null
            Stage.HANDOVER_TO_VEHICLE -> "我已上车" to null
            Stage.VEHICLE_OBSERVATION -> "查看路况" to null
            Stage.TAKEOVER_L2 -> "让 AURI 接管" to null
            Stage.PLANNING -> null to null
            Stage.WAITING_CONFIRMATION -> {
                if (ws.confirmation != null && ws.confirmation!!.status == ConfirmationStatus.PENDING) null to null
                else "继续" to null
            }
            Stage.ACTION_COMPLETED -> "重新演示" to "重置"
            Stage.COOLDOWN -> "查看复盘" to null
            Stage.PARKED_REVIEW -> "重新开始" to "重置"
            Stage.ERROR -> "重试" to "重置"
            else -> null to null
        }
    }
}
