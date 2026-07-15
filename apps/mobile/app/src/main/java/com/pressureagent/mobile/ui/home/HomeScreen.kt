package com.pressureagent.mobile.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.common.RiskBanner
import com.pressureagent.mobile.ui.common.TaskCard
import com.pressureagent.mobile.ui.common.VehicleCard
import com.pressureagent.mobile.ui.common.WearableBar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToVoiceChat: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("随行压力接管", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!state.isIdle) {
                    StageChip(stageLabel = state.stageLabel, modeLabel = state.agentModeLabel)
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onNavigateToVoiceChat) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "AI 语音助手",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (state.isLoading && state.isIdle) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (state.isIdle) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Text("暂无任务", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        // Tasks
        if (state.tasks.isNotEmpty()) {
            Text("今日任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            state.tasks.forEach { TaskCard(task = it) }
        }

        // Risk + Vehicle
        if (state.risk != null && state.risk!!.level != RiskLevel.NONE) {
            RiskBanner(risk = state.risk!!, conclusion = state.conclusion)
            if (state.vehicle != null) {
                VehicleCard(vehicle = state.vehicle!!)
            }
        }

        // Messages + Confirmation
        if (state.messages.isNotEmpty()) {
            Text("消息草稿", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            state.messages.forEach { msg ->
                MessageCard(message = msg)
            }
            if (state.confirmation != null && state.confirmation!!.status == ConfirmationStatus.PENDING) {
                ConfirmationBar(
                    prompt = state.confirmation!!.prompt,
                    onConfirm = viewModel::confirm,
                    onReject = viewModel::reject,
                )
            }
        }

        // Wearable
        if (state.wearable != null) {
            WearableBar(wearable = state.wearable!!)
        }

        // Error
        state.error?.let {
            Snackbar(modifier = Modifier.fillMaxWidth()) { Text(it) }
        }
    }
}

@Composable
private fun StageChip(stageLabel: String, modeLabel: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "$stageLabel · $modeLabel",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MessageCard(message: Message) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${if (message.audience == Audience.TEACHER) "👩‍🏫" else "👨‍👩‍👧"} ${message.displayName ?: ""}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val (label, color) = when (message.status) {
                    MessageStatus.DRAFT -> "草稿" to MaterialTheme.colorScheme.surfaceVariant
                    MessageStatus.AWAITING_CONFIRMATION -> "待确认" to MaterialTheme.colorScheme.tertiaryContainer
                    MessageStatus.SIMULATED_SENT -> "已发送" to MaterialTheme.colorScheme.primaryContainer
                }
                Surface(shape = MaterialTheme.shapes.extraSmall, color = color) {
                    Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(message.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConfirmationBar(prompt: String, onConfirm: () -> Unit, onReject: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📋 $prompt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("拒绝") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确认发送") }
            }
        }
    }
}
