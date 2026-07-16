package com.pressureagent.mobile.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

// ─── TaskCard (v0.2) ─────────────────────────────────────────────────────────

@Composable
fun TaskCard(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (task.status) {
                TaskStatus.COMPLETED -> Color(0xFFF0F0F0)
                TaskStatus.RESCHEDULED -> AuriWarning.copy(alpha = 0.08f)
                TaskStatus.PENDING -> Color.White
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (task.taskType == TaskType.RIGID) AuriCritical.copy(alpha = 0.1f)
                           else AuriSuccess.copy(alpha = 0.1f),
                ) {
                    Text(
                        if (task.taskType == TaskType.RIGID) "刚性" else "弹性",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.taskType == TaskType.RIGID) AuriCritical else AuriSuccess,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (task.location != null) Text("📍 ${task.location}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (task.scheduledAt != null) Text("🕐 ${formatTime(task.scheduledAt)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (task.waitingParty.isNotEmpty()) {
                Text("等待: ${task.waitingParty.joinToString("、")}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TaskStatusChip(status = task.status)
                Text(
                    when (task.priority) { Priority.HIGH -> "高优先"; Priority.MEDIUM -> "中优先"; Priority.LOW -> "低优先" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
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

// ─── RiskBanner (v0.2) ──────────────────────────────────────────────────────

@Composable
fun RiskBanner(risk: Risk, conclusion: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (risk.pressureLevel) {
                PressureLevel.L3 -> AuriCritical.copy(alpha = 0.08f)
                PressureLevel.L2 -> AuriWarning.copy(alpha = 0.1f)
                PressureLevel.L1 -> AuriWarning.copy(alpha = 0.05f)
                PressureLevel.RECOVERY -> AuriSuccess.copy(alpha = 0.08f)
                PressureLevel.L0 -> Color.Transparent
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("风险评估", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AuriNavy)
            if (risk.reasonCodes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                risk.reasonCodes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = AuriNavy.copy(alpha = 0.7f)) }
            }
            if (conclusion != null) {
                Spacer(Modifier.height(8.dp)); HorizontalDivider(color = Color(0xFFE0E0E0)); Spacer(Modifier.height(8.dp))
                Text(conclusion, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AuriNavy)
            }
        }
    }
}

// ─── VehicleCard (v0.2 — uses scene + eta) ───────────────────────────────────

@Composable
fun VehicleCard(scene: Scene, eta: String?) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AuriProcessing.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("车辆状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriNavy)
            Spacer(Modifier.height(4.dp))
            Text("场景: ${when (scene) {
                Scene.DRIVING -> "驾驶中"; Scene.HIGH_LOAD_DRIVING -> "高负荷驾驶"
                Scene.PARKED -> "已停车"; Scene.APPROACHING_VEHICLE -> "接近车辆"
                Scene.OFF_VEHICLE -> "未在车内"
            }}", color = AuriNavy.copy(alpha = 0.7f))
            if (eta != null) Text("ETA: ${formatTime(eta)}", color = AuriNavy.copy(alpha = 0.7f))
        }
    }
}

// ─── WearableBar (v0.2) ─────────────────────────────────────────────────────

@Composable
fun WearableBar(wearable: Wearable) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!wearable.connected) Color(0xFFF5F5F5)
            else if (wearable.mode == WearableMode.WARNING || wearable.mode == WearableMode.ERROR)
                AuriCritical.copy(alpha = 0.08f)
            else Color.White,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⌚ 腕上设备", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Surface(shape = RoundedCornerShape(6.dp), color = if (wearable.connected) AuriSuccess.copy(alpha = 0.1f) else Color(0xFFF0F0F0)) {
                    Text(if (wearable.connected) "已连接" else "未连接",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (wearable.connected) AuriSuccess else Color.Gray)
                }
            }
            if (wearable.connected) {
                Spacer(Modifier.height(4.dp))
                Text("模式: ${wearable.mode.name}", color = AuriNavy.copy(alpha = 0.7f))
                if (wearable.text.isNotEmpty()) Text("屏幕: \"${wearable.text}\"", color = Color.Gray)
                if (wearable.heartRate != null) Text("心率: ${wearable.heartRate} bpm", color = Color.Gray)
            }
        }
    }
}

// ─── Util ────────────────────────────────────────────────────────────────────

internal fun formatTime(iso: String): String = try { iso.split("T").getOrNull(1)?.substring(0, 5) ?: iso } catch (_: Exception) { iso }
