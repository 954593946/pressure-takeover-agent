package com.pressureagent.mobile.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.ui.theme.*

@Composable
fun DebugScreen(viewModel: DebugViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("调试控制", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(12.dp))

        if (!viewModel.isAvailable) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("调试控制在 mock 模式下可用", color = Color.Gray)
            }
            return@Column
        }

        // Story progress
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mock 故事脚本 (v0.2)", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Spacer(Modifier.height(4.dp))
                Text("当前步骤: ${state.currentStep} / ${state.totalSteps - 1}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(stepDescription(state.currentStep), style = MaterialTheme.typography.bodySmall, color = AuriNavy.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::reset, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("重置") }
                    Button(onClick = viewModel::advance, modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AuriNavy)) { Text("下一步 →") }
                }
                Spacer(Modifier.height(12.dp))
                // Quick jump chips
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        0 to "空闲", 1 to "任务", 3 to "交接",
                        5 to "L2接管", 7 to "确认", 8 to "完成",
                    ).forEach { (step, label) ->
                        SuggestionChip(
                            onClick = { viewModel.jumpTo(step) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (step == state.currentStep) SuggestionChipDefaults.suggestionChipColors(
                                containerColor = AuriNavy.copy(alpha = 0.1f),
                            ) else SuggestionChipDefaults.suggestionChipColors(),
                        )
                    }
                }
            }
        }
    }
}

private fun stepDescription(step: Int): String = when (step) {
    0 -> "off_vehicle_idle — 初始空闲"
    1 -> "pre_departure_warning — 任务已创建"
    2 -> "meeting_overrun — 会议延迟 L1"
    3 -> "handover_to_vehicle — 交接车机"
    4 -> "vehicle_observation — 驾驶观察"
    5 -> "takeover_L2 — 拥堵 L2 接管"
    6 -> "planning — 规划动作+服务"
    7 -> "waiting_confirmation — 等待确认"
    8 -> "action_completed — 已完成"
    else -> "?"
}
