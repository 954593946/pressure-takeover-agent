package com.pressureagent.mobile.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

// ─── 豆包风格 ChatScreen：对话即一切 ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    var textInput by remember { mutableStateOf("") }
    var isTextMode by remember { mutableStateOf(false) }

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        containerColor = AuriIvory,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AuriNavy,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A", color = AuriGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("AURI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AuriNavy)
                            if (!state.isCompanionMode && state.stage != Stage.OFF_VEHICLE_IDLE) {
                                Text(state.stageLabel, style = MaterialTheme.typography.labelSmall, color = AuriNavy.copy(alpha = 0.5f))
                            }
                        }
                    }
                },
                actions = {
                    if (state.isCompanionMode) {
                        Surface(shape = RoundedCornerShape(12.dp), color = AuriProcessing.copy(alpha = 0.1f)) {
                            Text("车机主控", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = AuriProcessing, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (state.pressureLevel != PressureLevel.L0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (state.pressureLevel) {
                                PressureLevel.L1 -> AuriWarning.copy(alpha = 0.15f)
                                PressureLevel.L2 -> AuriWarning.copy(alpha = 0.25f)
                                PressureLevel.L3 -> AuriCritical.copy(alpha = 0.2f)
                                PressureLevel.RECOVERY -> AuriSuccess.copy(alpha = 0.15f)
                                else -> Color.Transparent
                            },
                        ) {
                            Text(
                                state.pressureLevel.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (state.pressureLevel) {
                                    PressureLevel.L1 -> AuriWarning
                                    PressureLevel.L2 -> Color(0xFFCC8800)
                                    PressureLevel.L3 -> AuriCritical
                                    PressureLevel.RECOVERY -> AuriSuccess
                                    else -> Color.Gray
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuriIvory),
            )
        },
        bottomBar = {
            VoiceInputBar(
                text = textInput,
                onTextChange = { textInput = it },
                isTextMode = isTextMode,
                onToggleMode = { isTextMode = !isTextMode },
                isListening = state.isListening,
                isCompanionMode = state.isCompanionMode,
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.onTextSubmit(textInput.trim())
                        textInput = ""
                        focusManager.clearFocus()
                    }
                },
                onStartVoice = viewModel::onVoiceToggle,
                onStopVoice = viewModel::onVoiceToggle,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Loading
            if (state.isLoading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuriNavy)
                    }
                }
            }

            // Companion mode banner
            if (state.isCompanionMode) {
                item(key = "companion") {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AuriProcessing.copy(alpha = 0.08f))) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🚗", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("驾驶中 — 请查看车机屏幕", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                                if (state.conclusion.isNotBlank()) {
                                    Text(state.conclusion, style = MaterialTheme.typography.bodySmall, color = AuriNavy.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }

            // Welcome message when idle
            if (state.stage == Stage.OFF_VEHICLE_IDLE && state.chatMessages.isEmpty()) {
                item(key = "welcome") {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("你只管开，我来处理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AuriNavy, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Text("AURI 随行压力接管 Agent\n下方语音说出你的任务，我来帮你安排", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 22.sp)
                        }
                    }
                }
            }

            // Chat messages with embedded rich cards
            items(state.chatMessages, key = { it.id }) { chat ->
                ChatBubble(chat = chat)
            }

            item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─── Chat Bubble ──────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(chat: ChatItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (chat.isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!chat.isUser) {
            Surface(shape = CircleShape, color = AuriNavy, modifier = Modifier.size(28.dp).align(Alignment.Top)) {
                Box(contentAlignment = Alignment.Center) { Text("A", color = AuriGold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (chat.isUser) Alignment.End else Alignment.Start,
        ) {
            // Text content
            if (chat.text.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (chat.isUser) 16.dp else 4.dp,
                        bottomEnd = if (chat.isUser) 4.dp else 16.dp,
                    ),
                    color = if (chat.isUser) AuriNavy else Color.White,
                    shadowElevation = if (chat.isUser) 0.dp else 1.dp,
                ) {
                    Text(
                        chat.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (chat.isUser) Color.White else AuriNavy,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Embedded rich card
            chat.richCard?.let { card ->
                Spacer(Modifier.height(6.dp))
                when (card) {
                    is RichCard.RiskInfo -> InlineRiskCard(card.risk, card.conclusion, card.eta)
                    is RichCard.TaskList -> InlineTaskListCard(card.tasks)
                    is RichCard.ServicePlan -> InlineServiceOrderCard(card.order)
                    is RichCard.MessageDraft -> InlineMessageCard(card.action)
                    is RichCard.ConfirmRequest -> InlineConfirmationCard(card.confirmationId, card.prompt)
                }
            }
        }

        if (chat.isUser) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = AuriGold.copy(alpha = 0.3f), modifier = Modifier.size(28.dp).align(Alignment.Top)) {
                Box(contentAlignment = Alignment.Center) { Text("我", color = AuriNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Inline Rich Cards ────────────────────────────────────────────────────

@Composable
private fun InlineRiskCard(risk: Risk, conclusion: String, eta: String?) {
    val accentColor = when (risk.pressureLevel) {
        PressureLevel.L3 -> AuriCritical
        PressureLevel.L2 -> Color(0xFFCC8800)
        PressureLevel.L1 -> AuriWarning
        PressureLevel.RECOVERY -> AuriSuccess
        else -> Color.Gray
    }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(risk.pressureLevel.name, fontWeight = FontWeight.Bold, color = accentColor)
                if (risk.lateMinutes > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = accentColor.copy(alpha = 0.15f)) {
                        Text("晚到 ${risk.lateMinutes} min", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = accentColor)
                    }
                }
            }
            if (risk.reasonCodes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                risk.reasonCodes.forEach { code -> Text("• ${reasonLabel(code)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            }
            if (eta != null) { Text("ETA: $eta", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
private fun InlineTaskListCard(tasks: List<Task>) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(14.dp)) {
            tasks.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = if (task.taskType == TaskType.RIGID) AuriCritical.copy(alpha = 0.2f) else AuriSuccess.copy(alpha = 0.2f), modifier = Modifier.size(8.dp)) {}
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.Medium, color = if (task.status == TaskStatus.COMPLETED) Color.Gray else AuriNavy)
                        if (task.scheduledAt != null) Text("🕐 ${formatTime(task.scheduledAt)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF0F0F0)) {
                        Text(if (task.taskType == TaskType.RIGID) "刚性" else "弹性", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (task != tasks.last()) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun InlineServiceOrderCard(order: ServiceOrder) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🛒 服务方案", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Text("¥%.1f".format(order.total), fontWeight = FontWeight.Bold, color = AuriNavy)
            }
            Spacer(Modifier.height(8.dp))
            order.items.take(4).forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.name} ×${item.quantity}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("¥%.1f".format(item.subtotal), style = MaterialTheme.typography.bodySmall, color = AuriNavy)
                }
            }
            if (order.items.size > 4) Text("…等 ${order.items.size} 项", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("配送 ${order.deliveryWindow}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Surface(shape = RoundedCornerShape(4.dp), color = if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) AuriSuccess.copy(alpha = 0.1f) else AuriCritical.copy(alpha = 0.1f)) {
                    Text(if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) "预算内" else "超预算", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun InlineMessageCard(action: Action) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋 消息草稿", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = AuriWarning.copy(alpha = 0.15f)) {
                    Text("待确认", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = AuriWarning)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(action.target, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = AuriNavy)
            Text(action.summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            action.detailsRef?.let { body ->
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF8F8F8)) {
                    Text("「$body」", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = AuriNavy.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun InlineConfirmationCard(confirmationId: String, prompt: String) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AuriNavy)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📋 $prompt", fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            // Confirmation buttons use the parent ChatViewModel via ambient — simplified here
            // The actual interactivity is wired through ChatViewModel.confirm/reject
        }
    }
}

// ─── Voice Input Bar ──────────────────────────────────────────────────────

@Composable
private fun VoiceInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isTextMode: Boolean,
    onToggleMode: () -> Unit,
    isListening: Boolean,
    isCompanionMode: Boolean,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (isTextMode) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onToggleMode) { Icon(Icons.Filled.Mic, contentDescription = "语音", tint = AuriNavy) }
                    OutlinedTextField(
                        value = text, onValueChange = onTextChange,
                        placeholder = { Text("输入消息…", color = Color.Gray) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AuriNavy, unfocusedBorderColor = Color(0xFFE0E0E0)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                    )
                    IconButton(onClick = onSend, enabled = text.isNotBlank(), modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = if (text.isNotBlank()) AuriNavy else Color.Gray)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleMode) { Icon(Icons.Filled.Keyboard, contentDescription = "键盘", tint = AuriNavy.copy(alpha = 0.6f)) }
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFF5F5F5),
                        onClick = { if (!isCompanionMode) onStartVoice() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isListening) {
                                    PulseDot()
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在聆听…", color = AuriCritical, fontSize = 14.sp)
                                } else {
                                    Text(if (isCompanionMode) "驾驶中 — 车机主控" else "按住说话，比如「帮我处理一下」", color = if (isCompanionMode) Color.Gray.copy(alpha = 0.5f) else Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(if (isListening) AuriCritical else AuriNavy), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { if (!isCompanionMode) { if (isListening) onStopVoice() else onStartVoice() } }, modifier = Modifier.size(44.dp)) {
                            Icon(if (isListening) Icons.Filled.Close else Icons.Filled.Mic, contentDescription = if (isListening) "停止" else "语音", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(600)), label = "pulse_alpha")
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AuriCritical.copy(alpha = alpha)))
}

// ─── Helpers ──────────────────────────────────────────────────────────────

private fun formatTime(iso: String): String = try {
    iso.split("T").getOrNull(1)?.substring(0, 5) ?: iso
} catch (_: Exception) { iso }

private fun reasonLabel(code: String): String = when (code) {
    "meeting_overrun" -> "会议超时"
    "departure_delayed" -> "出发延迟"
    "departure_window_narrowing" -> "出发时间窗口缩小"
    "en_route" -> "行驶中"
    "eta_on_time" -> "ETA 预计准点"
    "traffic_congestion" -> "交通拥堵"
    "rigid_task_impacted" -> "刚性任务受影响"
    "eta_exceeds_deadline" -> "ETA 超出截止时间"
    "notified_teacher_and_family" -> "已通知老师与家人"
    else -> code
}
