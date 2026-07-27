package com.pressureagent.mobile.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToReview: () -> Unit = {},
    onNavigateToWearable: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ─── Header ────────────────────────────────────────────────────────
        Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(4.dp))
        val activePreset = viewModel.activePreset
        val profileLabel = when (activePreset) {
            ProfilePreset.EFFICIENCY -> "效率优先"
            ProfilePreset.QUALITY -> "品质优先"
            null -> "未设置"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("偏好方案：$profileLabel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (state.isSyncing) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = AuriNavy)
                Spacer(Modifier.width(6.dp))
                Text("同步中…", style = MaterialTheme.typography.labelSmall, color = AuriNavy)
            }
        }
        Spacer(Modifier.height(20.dp))

        // ─── Sync error ────────────────────────────────────────────────────
        if (state.syncError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = AuriCritical.copy(alpha = 0.1f),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(state.syncError ?: "", style = MaterialTheme.typography.bodySmall, color = AuriCritical, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = AuriCritical, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ─── 停车复盘入口 ──────────────────────────────────────────────────
        EntryCard(
            icon = "📊",
            title = "停车复盘",
            subtitle = state.reviewSummary,
            enabled = state.hasReviewData,
            onClick = onNavigateToReview,
        )

        Spacer(Modifier.height(10.dp))

        // ─── 偏好设置（可切换）─────────────────────────────────────────────
        Text(
            "偏好设置",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AuriNavy,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        // Preset cards — click to switch
        ProfilePreset.entries.forEach { preset ->
            val isActive = activePreset == preset
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) AuriNavy else Color.White,
                ),
                border = if (isActive) null else BorderStroke(1.dp, Color(0xFFE0E0E0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable(enabled = !state.isSyncing) {
                        viewModel.switchToPreset(preset)
                    },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = when (preset) {
                        ProfilePreset.EFFICIENCY -> "🏃"
                        ProfilePreset.QUALITY -> "☕"
                    }
                    Text(icon, fontSize = 28.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            preset.label,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) Color.White else AuriNavy,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            preset.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) AuriGold.copy(alpha = 0.8f) else Color.Gray,
                        )
                    }
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AuriGold,
                        ) {
                            Text(
                                "当前",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AuriNavy,
                            )
                        }
                    }
                }
            }
        }

        // Detail of active profile
        state.profile?.let { profile ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("当前偏好详情", fontWeight = FontWeight.SemiBold, color = AuriNavy, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    DetailRow("语气", profile.tone)
                    DetailRow("语音主动阈值", profile.proactiveVoiceThreshold.name)
                    DetailRow("触觉模式", when (profile.hapticMode) {
                        HapticMode.CLEAR -> "清晰"
                        HapticMode.GENTLE -> "柔和"
                    })
                    DetailRow("预算上限", "¥${profile.budgetLimit.toInt()}")
                    DetailRow("配送策略", when (profile.deliveryPriority) {
                        DeliveryPriority.FASTEST -> "最快送达"
                        DeliveryPriority.QUALITY_FIRST -> "品质优先"
                    })
                    DetailRow("替代策略", when (profile.substitutionPolicy) {
                        SubstitutionPolicy.SAME_SPEC_WITHIN_BUDGET -> "同规格 · 预算内"
                        SubstitutionPolicy.SAME_BRAND_ONLY -> "同品牌"
                    })
                    DetailRow("解释深度", when (profile.explanationDepth) {
                        ExplanationDepth.BRIEF -> "简洁"
                        ExplanationDepth.DETAILED -> "详细"
                    })
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        // ─── 腕上设备 ──────────────────────────────────────────────────────
        val wearableSubtitle = state.wearable?.let { w ->
            val mode = when (w.mode) {
                WearableMode.IDLE -> "待命"
                WearableMode.WARNING -> "⚠ 预警"
                WearableMode.HANDOVER -> "🤝 交接"
                WearableMode.PROCESSING -> "🔄 处理中"
                WearableMode.COMPLETED -> "✅ 完成"
                WearableMode.ERROR -> "❌ 异常"
            }
            "${if (w.connected) "已连接" else "未连接"} · $mode"
        } ?: "未配对"
        EntryCard(
            icon = "⌚",
            title = "腕上设备",
            subtitle = wearableSubtitle,
            onClick = onNavigateToWearable,
        )

        Spacer(Modifier.height(10.dp))

        // ─── 调试模式 ──────────────────────────────────────────────────────
        EntryCard(
            icon = "🧪",
            title = "调试模式",
            subtitle = "Mock 状态跳转、事件手动提交",
            onClick = onNavigateToDebug,
        )

        Spacer(Modifier.height(10.dp))

        // ─── 日志查看器 ──────────────────────────────────────────────────────
        EntryCard(
            icon = "📋",
            title = "日志查看器",
            subtitle = "查看 App 运行日志，排查问题",
            onClick = onNavigateToLogViewer,
        )

        Spacer(Modifier.height(10.dp))

        // ─── 关于 ──────────────────────────────────────────────────────────
        EntryCard(
            icon = "ℹ️",
            title = "关于 AURI",
            subtitle = "v0.3 · 随行压力接管 Agent",
            onClick = { /* TODO */ },
        )

        Spacer(Modifier.height(24.dp))

        // ─── Demo note ─────────────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AuriWarning.copy(alpha = 0.08f)),
        ) {
            Text(
                "Demo 模式 · 偏好设置不影响安全权限（L0-L3 阈值、确认权、主交互端仍由确定性代码决定）",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = AuriNavy.copy(alpha = 0.6f),
            )
        }
    }
}

// ─── Detail Row ────────────────────────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = AuriNavy)
    }
}

// ─── Entry Card ───────────────────────────────────────────────────────────

@Composable
private fun EntryCard(
    icon: String,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF8F8F8)),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) AuriNavy else Color.Gray, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (enabled) {
                Text("→", color = Color.Gray.copy(alpha = 0.5f), fontSize = 18.sp)
            }
        }
    }
}
