package com.pressureagent.mobile.ui.wearable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

@Composable
fun WearableScreen(viewModel: WearableViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("腕上设备", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(12.dp))

        if (state.wearable == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("等待数据…", color = Color.Gray)
            }
            return@Column
        }

        state.wearable?.let { w ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Connection status
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⌚ 腕上设备", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                        Surface(shape = RoundedCornerShape(8.dp), color = if (w.connected) AuriSuccess.copy(alpha = 0.1f) else Color(0xFFF0F0F0)) {
                            Text(
                                if (w.connected) "已连接" else "未连接",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (w.connected) AuriSuccess else Color.Gray,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // Mode
                    DetailRow("模式", modeLabel(w.mode))
                    DetailRow("显示文字", w.text.ifEmpty { "—" })
                    DetailRow("颜色", w.color.name)
                    DetailRow("触觉", hapticLabel(w.haptic))
                    if (w.heartRate != null) DetailRow("心率", "${w.heartRate} bpm")
                    if (w.signalConfidence != null) DetailRow("信号置信度", "%.0f%%".format(w.signalConfidence * 100))
                    if (w.commandId.isNotEmpty()) DetailRow("Command ID", w.commandId)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AuriNavy)
    }
}

private fun modeLabel(m: WearableMode): String = when (m) {
    WearableMode.IDLE -> "待命"
    WearableMode.WARNING -> "⚠ 预警"
    WearableMode.HANDOVER -> "🤝 交接"
    WearableMode.PROCESSING -> "🔄 处理中"
    WearableMode.COMPLETED -> "✅ 完成"
    WearableMode.ERROR -> "❌ 异常"
}

private fun hapticLabel(h: HapticPattern): String = when (h) {
    HapticPattern.NONE -> "无"
    HapticPattern.DOUBLE_SHORT -> "双短震"
    HapticPattern.SINGLE_PULSE -> "单脉冲"
    HapticPattern.THREE_BEAT -> "三拍"
    HapticPattern.SOFT_SHORT -> "柔和短震"
    HapticPattern.ERROR_ONCE -> "错误震"
}
