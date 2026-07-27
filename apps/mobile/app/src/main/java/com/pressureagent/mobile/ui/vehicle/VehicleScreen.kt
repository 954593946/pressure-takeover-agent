package com.pressureagent.mobile.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

@Composable
fun VehicleScreen(viewModel: VehicleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ─── Header ────────────────────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(state.vehicleInfo.modelName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = if (state.vehicleInfo.connected) AuriSuccess else Color.Gray, modifier = Modifier.size(8.dp)) {}
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.vehicleInfo.connected) "已连接" else "未连接", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        state.vehicleInfo.licensePlate?.let { plate ->
                            Spacer(Modifier.width(12.dp))
                            Text(plate, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // ─── Core status cards ─────────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusMetricCard(icon = "🔋", label = "续航里程", value = "${state.vehicleStatus.rangeKm}", unit = "km", modifier = Modifier.weight(1f))
                StatusMetricCard(icon = "⚡", label = "电池电量", value = "${state.vehicleStatus.batteryPercent}", unit = "%", modifier = Modifier.weight(1f))
                StatusMetricCard(icon = "🛣", label = "总里程", value = "${state.vehicleStatus.totalOdometerKm}", unit = "km", modifier = Modifier.weight(1f))
            }
        }

        // ─── Battery bar ───────────────────────────────────────────────────
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("电池状态", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                        Text("${state.vehicleStatus.batteryPercent}%", fontWeight = FontWeight.Bold, color = AuriNavy)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Custom progress bar
                    val batteryColor = when {
                        state.vehicleStatus.batteryPercent > 60 -> AuriSuccess
                        state.vehicleStatus.batteryPercent > 30 -> AuriWarning
                        else -> AuriCritical
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEEEEE))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(fraction = state.vehicleStatus.batteryPercent / 100f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(batteryColor, batteryColor.copy(alpha = 0.7f))
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("预计续航 ${state.vehicleStatus.rangeKm} km", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }

        // ─── Quick status grid 2×2 ─────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickStatusCard(
                    icon = if (state.vehicleStatus.isLocked) "🔒" else "🔓",
                    label = "门锁",
                    value = if (state.vehicleStatus.isLocked) "已上锁" else "已解锁",
                    modifier = Modifier.weight(1f),
                )
                QuickStatusCard(
                    icon = "🌡",
                    label = "空调",
                    value = "${state.vehicleStatus.cabinTempCelsius}°C",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickStatusCard(
                    icon = "📍",
                    label = "位置",
                    value = state.vehicleStatus.location ?: "未知",
                    modifier = Modifier.weight(1f),
                )
                QuickStatusCard(
                    icon = "🛡",
                    label = "安防",
                    value = if (state.vehicleStatus.securityStatus == "normal") "正常" else "⚠ 警报",
                    valueColor = if (state.vehicleStatus.securityStatus != "normal") AuriCritical else AuriNavy,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ─── HMI link panel ────────────────────────────────────────────────
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AuriNavy)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("🖥️ HMI 车机联动", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    HmiInfoRow("主控权", when (state.hmiPrimarySurface) {
                        PrimarySurface.VEHICLE_HMI -> "车机主控"
                        PrimarySurface.MOBILE -> "手机主控"
                        PrimarySurface.NONE -> "无"
                    })
                    HmiInfoRow("驾驶场景", sceneLabel(state.hmiScene))
                    HmiInfoRow("当前阶段", stageLabel(state.hmiStage))
                    HmiInfoRow("压力级别", state.hmiRisk.pressureLevel.name)
                    if (state.hmiEta != null) {
                        HmiInfoRow("预计到达", state.hmiEta!!)
                    }
                }
            }
        }

        // ─── Trip history ──────────────────────────────────────────────────
        item {
            Text("行程历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriNavy)
        }

        items(state.recentTrips, key = { it.tripId }) { trip ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${trip.origin} → ${trip.destination}", fontWeight = FontWeight.Medium, color = AuriNavy)
                            if (trip.hadTakeover) {
                                Spacer(Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = AuriProcessing.copy(alpha = 0.1f)) {
                                    Text("AURI 接管", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = AuriProcessing)
                                }
                            }
                        }
                        Row {
                            Text(trip.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(Modifier.width(16.dp))
                            Text("${trip.durationMinutes} min", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(Modifier.width(16.dp))
                            Text("${trip.distanceKm} km", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────

@Composable
private fun StatusMetricCard(icon: String, label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AuriNavy)
                Spacer(Modifier.width(2.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun QuickStatusCard(icon: String, label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = AuriNavy) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = modifier) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(value, fontWeight = FontWeight.SemiBold, color = valueColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HmiInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = AuriGold, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

// ─── Labels ────────────────────────────────────────────────────────────────

private fun sceneLabel(s: Scene): String = when (s) {
    Scene.OFF_VEHICLE -> "离车"
    Scene.APPROACHING_VEHICLE -> "接近车辆"
    Scene.DRIVING -> "城市通勤"
    Scene.HIGH_LOAD_DRIVING -> "高负荷驾驶"
    Scene.PARKED -> "已停车"
}

private fun stageLabel(s: Stage): String = when (s) {
    Stage.OFF_VEHICLE_IDLE -> "待命"
    Stage.PRE_DEPARTURE_WARNING -> "出发预警"
    Stage.HANDOVER_TO_VEHICLE -> "交接车机"
    Stage.VEHICLE_OBSERVATION -> "驾驶观察"
    Stage.TAKEOVER_L2 -> "L2 协调接管"
    Stage.TAKEOVER_L3 -> "L3 高负荷保护"
    Stage.PLANNING -> "规划中"
    Stage.SERVICE_PREPARED -> "方案就绪"
    Stage.WAITING_CONFIRMATION -> "等待确认"
    Stage.EXECUTING -> "执行中"
    Stage.SERVICE_EXECUTED -> "已执行"
    Stage.ACTION_COMPLETED -> "已完成"
    Stage.COOLDOWN -> "冷却中"
    Stage.PARKED_REVIEW -> "停车复盘"
    Stage.ERROR -> "异常"
}
