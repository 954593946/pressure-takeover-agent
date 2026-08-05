package com.pressureagent.mobile.ui.wearable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
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
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearableScreen(viewModel: WearableViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("腕上设备", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("⌚ 腕上设备", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                    val connected = state.wearable?.connected == true || state.gateway.isWatchRecentlySeen()
                    Surface(shape = RoundedCornerShape(8.dp), color = if (connected) AuriSuccess.copy(alpha = 0.1f) else Color(0xFFF0F0F0)) {
                        Text(
                            if (connected) "已连接" else "未连接",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connected) AuriSuccess else Color.Gray,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                state.wearable?.let { w ->
                    DetailRow("模式", modeLabel(w.mode))
                    DetailRow("显示文字", w.text.ifEmpty { "—" })
                    DetailRow("颜色", w.color.name)
                    DetailRow("触觉", hapticLabel(w.haptic))
                    if (w.heartRate != null) DetailRow("心率", "${w.heartRate} bpm")
                    if (w.signalConfidence != null) DetailRow("信号置信度", "%.0f%%".format(w.signalConfidence * 100))
                    if (w.commandId.isNotEmpty()) DetailRow("Command ID", w.commandId)
                } ?: DetailRow("状态", "等待 Agent 数据，可直接使用下方调试")
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Agent 联动状态", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Spacer(Modifier.height(12.dp))
                DetailRow("WorldState", state.stage?.let { stageLabel(it) } ?: "等待同步")
                DetailRow("Revision", state.revision.toString())
                DetailRow("主交互端", state.primarySurface?.let { primarySurfaceLabel(it) } ?: "—")
                DetailRow("联动命令", state.gateway.lastAgentCommandId.ifEmpty { "—" })
                DetailRow("联动模式", state.gateway.lastAgentCommandMode.ifEmpty { "—" })
                DetailRow("联动文案", state.gateway.lastAgentCommandText.ifEmpty { "—" })
                DetailRow("联动触觉", state.gateway.lastAgentCommandHaptic.ifEmpty { "—" })
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("手动调试", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Spacer(Modifier.height(12.dp))
                Text("状态切换", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugButton("待命", Modifier.weight(1f)) { viewModel.sendDebugState(WearableMode.IDLE) }
                    DebugButton("预警", Modifier.weight(1f)) { viewModel.sendDebugState(WearableMode.WARNING) }
                    DebugButton("交接", Modifier.weight(1f)) { viewModel.sendDebugState(WearableMode.HANDOVER) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugButton("处理中", Modifier.weight(1f)) { viewModel.sendDebugState(WearableMode.PROCESSING) }
                    DebugButton("完成", Modifier.weight(1f)) { viewModel.sendDebugState(WearableMode.COMPLETED) }
                    DebugButton("异常", Modifier.weight(1f)) { viewModel.sendDebugState(WearableMode.ERROR) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugButton("压力上升", Modifier.weight(1f)) { viewModel.sendDebugPressureRise() }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))
                Text("触觉测试", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugButton("双短震", Modifier.weight(1f)) { viewModel.sendDebugHaptic(HapticPattern.DOUBLE_SHORT) }
                    DebugButton("三拍", Modifier.weight(1f)) { viewModel.sendDebugHaptic(HapticPattern.THREE_BEAT) }
                    DebugButton("错误震", Modifier.weight(1f)) { viewModel.sendDebugHaptic(HapticPattern.ERROR_ONCE) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("本机网关", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.gateway.running) AuriSuccess.copy(alpha = 0.1f) else AuriCritical.copy(alpha = 0.08f),
                    ) {
                        Text(
                            if (state.gateway.running) "运行中" else "未运行",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.gateway.running) AuriSuccess else AuriCritical,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                DetailRow("地址", state.gateway.baseUrl)
                DetailRow("最近命令", state.gateway.lastOutboxCommandId.ifEmpty { "—" })
                DetailRow("命令来源", state.gateway.lastOutboxSource.ifEmpty { "—" })
                DetailRow("最近 ACK", summarizeAck(state.gateway.lastAck))
                DetailRow("最近 SENSOR", summarizeSensor(state.gateway.lastSensor))
                DetailRow("最近 PONG", summarizePong(state.gateway.lastPong))
                DetailRow("Zepp 侧连接", formatAge(state.gateway.lastSideContactAt))
                if (state.gateway.lastError.isNotEmpty()) {
                    DetailRow("错误", state.gateway.lastError)
                }

                Spacer(Modifier.height(16.dp))
                HealthSnapshotPanel(
                    sensor = state.gateway.lastSensor,
                    onClick = viewModel::requestHealthSnapshot,
                    onLongClick = viewModel::requestHealthSnapshot,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::requestHealthSnapshot,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuriNavy),
                ) {
                    Text("请求健康快照")
                }
            }
        }
    }
}

@Composable
private fun DebugButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HealthSnapshotPanel(
    sensor: JsonObject?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        color = AuriNavy.copy(alpha = 0.04f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("健康数据", fontWeight = FontWeight.SemiBold, color = AuriNavy)
            Spacer(Modifier.height(6.dp))
            if (sensor == null) {
                Text("点击或长按请求手表回传", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                DetailRow("心率", sensor.text("heart_rate").ifEmpty { "--" })
                DetailRow("血氧", sensor.text("spo2").ifEmpty { "--" })
                DetailRow("置信度", sensor.text("confidence").ifEmpty { "--" })
                DetailRow("时间戳", sensor.text("timestamp").ifEmpty { "--" })
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

private fun stageLabel(stage: Stage): String = when (stage) {
    Stage.OFF_VEHICLE_IDLE -> "off_vehicle_idle"
    Stage.PRE_DEPARTURE_WARNING -> "pre_departure_warning"
    Stage.HANDOVER_TO_VEHICLE -> "handover_to_vehicle"
    Stage.VEHICLE_OBSERVATION -> "vehicle_observation"
    Stage.TAKEOVER_L2 -> "takeover_L2"
    Stage.TAKEOVER_L3 -> "takeover_L3"
    Stage.PLANNING -> "planning"
    Stage.SERVICE_PREPARED -> "service_prepared"
    Stage.WAITING_CONFIRMATION -> "waiting_confirmation"
    Stage.EXECUTING -> "executing"
    Stage.SERVICE_EXECUTED -> "service_executed"
    Stage.ACTION_COMPLETED -> "action_completed"
    Stage.COOLDOWN -> "cooldown"
    Stage.PARKED_REVIEW -> "parked_review"
    Stage.ERROR -> "error"
}

private fun primarySurfaceLabel(surface: PrimarySurface): String = when (surface) {
    PrimarySurface.MOBILE -> "mobile"
    PrimarySurface.VEHICLE_HMI -> "vehicle_hmi"
    PrimarySurface.NONE -> "none"
}

private fun com.pressureagent.mobile.data.wearablegateway.WearableGatewaySnapshot.isWatchRecentlySeen(): Boolean {
    val lastSeen = lastSideContactAt
    return lastSeen > 0L && System.currentTimeMillis() - lastSeen <= 60_000L
}

private fun summarizeAck(ack: JsonObject?): String {
    if (ack == null) return "—"
    val result = ack.text("result").ifEmpty { "unknown" }
    val commandId = ack.text("command_id")
    return if (commandId.isEmpty()) result else "$result · $commandId"
}

private fun summarizeSensor(sensor: JsonObject?): String {
    if (sensor == null) return "—"
    val heartRate = sensor.text("heart_rate").ifEmpty { "--" }
    val spo2 = sensor.text("spo2").ifEmpty { "--" }
    return "HR $heartRate / O2 $spo2"
}

private fun summarizePong(pong: JsonObject?): String {
    if (pong == null) return "—"
    return pong.text("ping_id").ifEmpty { "received" }
}

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun formatAge(timestamp: Long): String {
    if (timestamp <= 0L) return "未收到"
    val seconds = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
    return "${seconds}s 前"
}
