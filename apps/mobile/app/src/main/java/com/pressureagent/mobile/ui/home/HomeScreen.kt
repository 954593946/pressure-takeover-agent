package com.pressureagent.mobile.ui.home

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.home.ChatItem
import com.pressureagent.mobile.ui.theme.*

// ─── 豆包风格 HomeScreen：语音一级入口 ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToServicePlan: () -> Unit = {},
    onNavigateToReview: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Text input
    var textInput by remember { mutableStateOf("") }
    var isTextMode by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new chat messages arrive
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        containerColor = AuriIvory,
        topBar = {
            // 极简顶栏 — 豆包风格
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // AURI 标识
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
                    // Companion mode badge
                    if (state.isCompanionMode) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AuriProcessing.copy(alpha = 0.1f),
                        ) {
                            Text(
                                "车机主控",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = AuriProcessing,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Pressure level (L0-L3)
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
                        Spacer(Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuriIvory),
            )
        },
        bottomBar = {
            // ─── 豆包风格语音输入栏 ──────────────────────────────────────────
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
                onStartVoice = {
                    viewModel.onVoiceToggle()
                },
                onStopVoice = {
                    viewModel.onVoiceToggle()
                },
            )
        },
        snackbarHost = {
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = viewModel::dismissError) { Text("关闭") } },
                ) { Text(error) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Loading ─────────────────────────────────────────────────────
            if (state.isLoading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuriNavy)
                    }
                }
            }

            // ─── Companion mode banner ────────────────────────────────────────
            if (state.isCompanionMode) {
                item(key = "companion") {
                    CompanionModeBanner(
                        primarySurface = state.primarySurface,
                        conclusion = state.conclusion,
                    )
                }
            }

            // ─── Primary action button (demo flow) ────────────────────────────
            state.primaryAction?.let { action ->
                item(key = "primary_action") {
                    Button(
                        onClick = viewModel::onPrimaryAction,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuriNavy),
                    ) {
                        Text(action, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            state.secondaryAction?.let { action ->
                item(key = "secondary_action") {
                    OutlinedButton(
                        onClick = viewModel::onSecondaryAction,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(action)
                    }
                }
            }

            // ─── Chat messages（对话气泡）──────────────────────────────────────
            items(state.chatMessages, key = { it.id }) { chat ->
                ChatBubble(chat = chat)
            }

            // ─── ETA + Risk ──────────────────────────────────────────────────
            if (state.risk != null && state.pressureLevel != PressureLevel.L0) {
                item(key = "risk") {
                    RiskBanner(risk = state.risk!!, conclusion = state.conclusion, eta = state.eta)
                }
            }

            // ─── Tasks ───────────────────────────────────────────────────────
            if (state.tasks.isNotEmpty()) {
                item(key = "task_header") {
                    Text("今日任务", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = AuriNavy)
                }
                items(state.tasks, key = { it.taskId }) { task ->
                    TaskCard(task = task)
                }
            }

            // ─── Service Orders ──────────────────────────────────────────────
            state.serviceOrders.forEach { order ->
                item(key = "order_${order.previewId}") {
                    ServiceOrderCard(order = order, onClick = onNavigateToServicePlan)
                }
            }

            // ─── Message actions (drafted messages) ──────────────────────────
            val messageActions = state.actions.filter {
                it.type == ActionType.MESSAGE && it.status != ActionStatus.COMPLETED
            }
            if (messageActions.isNotEmpty()) {
                item(key = "msg_header") {
                    Text("消息草稿", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = AuriNavy)
                }
                items(messageActions, key = { it.actionId }) { action ->
                    MessageActionCard(action = action)
                }
            }

            // ─── Confirmation ────────────────────────────────────────────────
            if (state.confirmation != null && state.confirmation!!.status == ConfirmationStatus.PENDING) {
                item(key = "confirm") {
                    ConfirmationCard(
                        prompt = state.output?.conclusion ?: "是否确认执行？",
                        onConfirm = viewModel::confirm,
                        onReject = viewModel::reject,
                    )
                }
            }

            // ─── Wearable status ─────────────────────────────────────────────
            state.wearable?.let { w ->
                item(key = "wearable") {
                    WearableBar(wearable = w)
                }
            }

            // ─── Idle state ──────────────────────────────────────────────────
            if (state.stage == Stage.OFF_VEHICLE_IDLE && state.tasks.isEmpty()) {
                item(key = "idle") {
                    IdleWelcomeCard()
                }
            }

            // Bottom spacer for input bar
            item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─── Voice Input Bar（豆包风格）────────────────────────────────────────────────

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
                // ─── Text input mode ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onToggleMode) {
                        Icon(Icons.Filled.Mic, contentDescription = "语音", tint = AuriNavy)
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = { Text("输入消息…", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuriNavy,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                    )
                    IconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送",
                            tint = if (text.isNotBlank()) AuriNavy else Color.Gray)
                    }
                }
            } else {
                // ─── Voice mode（默认）─ 豆包风格大语音按钮 ──────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Keyboard toggle on left
                    IconButton(onClick = onToggleMode) {
                        Icon(Icons.Filled.Keyboard, contentDescription = "键盘", tint = AuriNavy.copy(alpha = 0.6f))
                    }

                    // Voice pill — the main input surface
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFF5F5F5),
                        onClick = {
                            if (isCompanionMode) return@Surface
                            onStartVoice()
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isListening) {
                                    // Listening state
                                    PulseDot()
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在聆听…", color = AuriCritical, fontSize = 14.sp)
                                } else {
                                    Text(
                                        if (isCompanionMode) "驾驶中 — 车机主控"
                                        else "按住说话，比如「帮我处理一下」",
                                        color = if (isCompanionMode) Color.Gray.copy(alpha = 0.5f) else Color.Gray,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Mic button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isListening) AuriCritical else AuriNavy),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = {
                                if (isCompanionMode) return@IconButton
                                if (isListening) onStopVoice() else onStartVoice()
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                if (isListening) Icons.Filled.Close else Icons.Filled.Mic,
                                contentDescription = if (isListening) "停止" else "语音",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
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
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = androidx.compose.animation.core.tween(600)),
        label = "pulse_alpha",
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(AuriCritical.copy(alpha = alpha)),
    )
}

// ─── Companion Mode Banner ────────────────────────────────────────────────────

@Composable
private fun CompanionModeBanner(primarySurface: PrimarySurface, conclusion: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AuriProcessing.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🚗", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "驾驶中 — 请查看车机屏幕",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AuriNavy,
                )
                if (conclusion.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(conclusion, style = MaterialTheme.typography.bodySmall, color = AuriNavy.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ─── Cards ────────────────────────────────────────────────────────────────────

@Composable
private fun RiskBanner(risk: Risk, conclusion: String, eta: String?) {
    val (bgColor, accentColor, icon) = when (risk.pressureLevel) {
        PressureLevel.L3 -> Triple(AuriCritical.copy(alpha = 0.08f), AuriCritical, "🔴")
        PressureLevel.L2 -> Triple(AuriWarning.copy(alpha = 0.1f), Color(0xFFCC8800), "🟡")
        PressureLevel.L1 -> Triple(AuriWarning.copy(alpha = 0.05f), AuriWarning, "🟠")
        PressureLevel.RECOVERY -> Triple(AuriSuccess.copy(alpha = 0.08f), AuriSuccess, "🟢")
        else -> Triple(Color.Transparent, Color.Gray, "")
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = bgColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(risk.pressureLevel.name, fontWeight = FontWeight.Bold, color = accentColor)
                Spacer(Modifier.weight(1f))
                if (risk.lateMinutes > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = accentColor.copy(alpha = 0.15f)) {
                        Text(
                            "晚到 ${risk.lateMinutes} min",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (risk.reasonCodes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                risk.reasonCodes.forEach { reason ->
                    Text("• ${reasonLabel(reason)}", style = MaterialTheme.typography.bodySmall, color = AuriNavy.copy(alpha = 0.7f))
                }
            }
            if (eta != null) {
                Spacer(Modifier.height(4.dp))
                Text("ETA: ${formatTime(eta)}", style = MaterialTheme.typography.labelSmall, color = AuriNavy.copy(alpha = 0.5f))
            }
            if (conclusion.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = accentColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                Text(conclusion, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AuriNavy)
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.status == TaskStatus.COMPLETED) Color(0xFFF0F0F0) else Color.White,
        ),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, fontWeight = FontWeight.SemiBold, color = AuriNavy)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (task.taskType == TaskType.RIGID) AuriCritical.copy(alpha = 0.1f) else AuriSuccess.copy(alpha = 0.1f),
                    ) {
                        Text(
                            if (task.taskType == TaskType.RIGID) "刚性" else "弹性",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (task.taskType == TaskType.RIGID) AuriCritical else AuriSuccess,
                        )
                    }
                }
                if (task.location != null || task.scheduledAt != null) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        if (task.scheduledAt != null) Text("🕐 ${formatTime(task.scheduledAt)}  ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (task.location != null) Text("📍 ${task.location}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            // Priority badge
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF5F5F5)) {
                Text(
                    when (task.priority) { Priority.HIGH -> "高"; Priority.MEDIUM -> "中"; Priority.LOW -> "低" },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ServiceOrderCard(order: ServiceOrder, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🛒 服务方案", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Surface(shape = RoundedCornerShape(8.dp), color = AuriGold.copy(alpha = 0.15f)) {
                    Text(
                        if (order.status == ServiceOrderStatus.SUBMITTED) "已下单" else "预览",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuriGold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${order.items.size} 项商品", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text("¥%.1f".format(order.total), fontWeight = FontWeight.Bold, color = AuriNavy)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("配送 ${order.deliveryWindow}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Surface(shape = RoundedCornerShape(6.dp), color = if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) AuriSuccess.copy(alpha = 0.1f) else AuriCritical.copy(alpha = 0.1f)) {
                    Text(
                        if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) "预算内" else "超预算",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) AuriSuccess else AuriCritical,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionCard(action: Action) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(action.target, fontWeight = FontWeight.SemiBold, color = AuriNavy)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = when (action.status) {
                        ActionStatus.AWAITING_CONFIRMATION -> AuriWarning.copy(alpha = 0.15f)
                        ActionStatus.PLANNED -> Color(0xFFF0F0F0)
                        else -> AuriSuccess.copy(alpha = 0.1f)
                    }) {
                        Text(
                            when (action.status) {
                                ActionStatus.AWAITING_CONFIRMATION -> "待确认"
                                ActionStatus.PLANNED -> "已规划"
                                ActionStatus.COMPLETED -> "已完成"
                                else -> action.status.name
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(action.summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                action.detailsRef?.let { body ->
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF8F8F8)) {
                        Text(
                            "「$body」",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = AuriNavy.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationCard(prompt: String, onConfirm: () -> Unit, onReject: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AuriNavy),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("📋 $prompt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("拒绝") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuriGold),
                ) { Text("确认发送", color = AuriNavy, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ─── Chat Bubble ─────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(chat: ChatItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (chat.isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!chat.isUser) {
            // AURI avatar
            Surface(
                shape = CircleShape,
                color = AuriNavy,
                modifier = Modifier.size(28.dp).align(Alignment.Top),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("A", color = AuriGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (chat.isUser) Alignment.End else Alignment.Start,
        ) {
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

        if (chat.isUser) {
            Spacer(Modifier.width(8.dp))
            // User avatar
            Surface(
                shape = CircleShape,
                color = AuriGold.copy(alpha = 0.3f),
                modifier = Modifier.size(28.dp).align(Alignment.Top),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("我", color = AuriNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WearableBar(wearable: Wearable) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val emoji = when (wearable.mode) {
                WearableMode.WARNING -> "⚠️"
                WearableMode.PROCESSING -> "🔄"
                WearableMode.COMPLETED -> "✅"
                WearableMode.ERROR -> "❌"
                WearableMode.HANDOVER -> "🤝"
                else -> "⌚"
            }
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("腕上设备", fontWeight = FontWeight.SemiBold, color = AuriNavy, style = MaterialTheme.typography.bodyMedium)
                Row {
                    Text(wearable.text.ifEmpty { modeLabel(wearable.mode) }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (wearable.heartRate != null) {
                        Spacer(Modifier.width(12.dp))
                        Text("♥ ${wearable.heartRate}", style = MaterialTheme.typography.bodySmall, color = AuriCritical.copy(alpha = 0.7f))
                    }
                }
            }
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF5F5F5)) {
                Text(
                    hapticLabel(wearable.haptic),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun IdleWelcomeCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("你只管开，我来处理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AuriNavy, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "AURI 随行压力接管 Agent\n下方语音说出你的任务，我来帮你安排",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(20.dp))
            // Suggestion chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("创建任务") },
                    shape = RoundedCornerShape(12.dp),
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text("查看状态") },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

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

private fun modeLabel(mode: WearableMode): String = when (mode) {
    WearableMode.IDLE -> "待命"
    WearableMode.WARNING -> "预警"
    WearableMode.HANDOVER -> "交接"
    WearableMode.PROCESSING -> "处理中"
    WearableMode.COMPLETED -> "完成"
    WearableMode.ERROR -> "异常"
}

private fun hapticLabel(h: HapticPattern): String = when (h) {
    HapticPattern.NONE -> "无"
    HapticPattern.DOUBLE_SHORT -> "双短震"
    HapticPattern.SINGLE_PULSE -> "单脉冲"
    HapticPattern.THREE_BEAT -> "三拍"
    HapticPattern.SOFT_SHORT -> "柔和短震"
    HapticPattern.ERROR_ONCE -> "错误震"
}
