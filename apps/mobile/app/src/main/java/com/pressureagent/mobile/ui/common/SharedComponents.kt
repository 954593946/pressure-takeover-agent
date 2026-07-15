package com.pressureagent.mobile.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pressureagent.mobile.domain.model.*

// ─── TaskCard ────────────────────────────────────────────────────────────────

@Composable
fun TaskCard(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (task.status) {
                TaskStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
                TaskStatus.RESCHEDULED -> MaterialTheme.colorScheme.tertiaryContainer
                TaskStatus.PENDING -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (task.type == TaskType.RIGID) MaterialTheme.colorScheme.errorContainer
                           else MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        if (task.type == TaskType.RIGID) "刚性" else "弹性",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (task.location != null) Text("📍 ${task.location}", style = MaterialTheme.typography.bodySmall)
            if (task.scheduledAt != null) Text("🕐 ${formatTime(task.scheduledAt)}", style = MaterialTheme.typography.bodySmall)
            if (!task.waitingParties.isNullOrEmpty()) {
                Text("等待: ${task.waitingParties.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TaskStatusChip(status = task.status)
                Text(
                    when (task.priority) { Priority.HIGH -> "高优先" ; Priority.MEDIUM -> "中优先" ; Priority.LOW -> "低优先" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskStatusChip(status: TaskStatus) {
    val label = when (status) {
        TaskStatus.PENDING -> "待处理"
        TaskStatus.RESCHEDULED -> "已顺延"
        TaskStatus.COMPLETED -> "已完成"
    }
    AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

// ─── RiskBanner ──────────────────────────────────────────────────────────────

@Composable
fun RiskBanner(risk: Risk, conclusion: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (risk.level) {
                RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                RiskLevel.WATCH -> MaterialTheme.colorScheme.tertiaryContainer
                RiskLevel.NONE -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("风险评估", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = when (risk.level) {
                        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                        RiskLevel.WATCH -> MaterialTheme.colorScheme.tertiary
                        RiskLevel.NONE -> MaterialTheme.colorScheme.outline
                    },
                ) {
                    Text(
                        when (risk.level) {
                            RiskLevel.HIGH -> "⚠ 高风险"
                            RiskLevel.WATCH -> "⚠ 关注"
                            RiskLevel.NONE -> "✓ 无风险"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
            if (risk.reasons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                risk.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (conclusion != null) {
                Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(conclusion, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── VehicleCard ─────────────────────────────────────────────────────────────

@Composable
fun VehicleCard(vehicle: Vehicle) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("车辆状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("状态: ${when (vehicle.mode) { VehicleMode.DRIVING -> "驾驶中"; VehicleMode.PARKED -> "已停车"; VehicleMode.OFF -> "未启动" }}")
            if (vehicle.destination != null) Text("目的地: ${vehicle.destination}")
            if (vehicle.eta != null) Text("预计到达: ${formatTime(vehicle.eta)}")
            if (vehicle.delayMinutes > 0) Text("延误 ${vehicle.delayMinutes} 分钟", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── WearableBar ─────────────────────────────────────────────────────────────

@Composable
fun WearableBar(wearable: Wearable) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (!wearable.connected) MaterialTheme.colorScheme.surfaceVariant
            else if (wearable.state == WearableState.TAKING_OVER || wearable.state == WearableState.WARNING)
                MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⌚ 腕上设备", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(shape = MaterialTheme.shapes.extraSmall, color = if (wearable.connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (wearable.connected) "已连接" else "未连接", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (wearable.connected) {
                Spacer(Modifier.height(4.dp))
                Text("状态: ${when (wearable.state) {
                    WearableState.IDLE -> "待命"; WearableState.READY -> "就绪"; WearableState.WARNING -> "⚠ 预警"
                    WearableState.DRIVING -> "驾驶中"; WearableState.TAKING_OVER -> "🆘 接管中"
                    WearableState.RESOLVED -> "已解决"; WearableState.OFFLINE -> "离线"
                }}")
                if (wearable.text != null) Text("屏幕: \"${wearable.text}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("震动: ${when (wearable.vibration) { Vibration.NONE -> "无"; Vibration.SHORT -> "短震"; Vibration.RHYTHMIC -> "节奏震动 ⚡" }}")
                if (wearable.heartRate != null) Text("心率: ${wearable.heartRate} bpm (${if (wearable.heartRateSource == HeartRateSource.DEVICE) "设备" else "模拟"})")
            }
        }
    }
}

// ─── Util ────────────────────────────────────────────────────────────────────

internal fun formatTime(iso: String): String = try { iso.split("T").getOrNull(1)?.substring(0, 5) ?: iso } catch (_: Exception) { iso }
