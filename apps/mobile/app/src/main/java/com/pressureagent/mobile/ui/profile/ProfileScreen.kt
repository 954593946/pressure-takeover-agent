package com.pressureagent.mobile.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToReview: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
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
        val profileTypeLabel = when (state.profile?.profileType) {
            ProfileType.EFFICIENCY -> "效率优先"
            ProfileType.QUALITY -> "品质优先"
            null -> "未设置"
        }
        Text("偏好方案：$profileTypeLabel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(20.dp))

        // ─── 停车复盘入口 ──────────────────────────────────────────────────
        EntryCard(
            icon = "📊",
            title = "停车复盘",
            subtitle = state.reviewSummary,
            enabled = state.hasReviewData,
            onClick = onNavigateToReview,
        )

        Spacer(Modifier.height(10.dp))

        // ─── 偏好设置 ──────────────────────────────────────────────────────
        EntryCard(
            icon = "⚙️",
            title = "偏好设置",
            subtitle = "交互风格、预算、语音主动阈值、解释深度",
            onClick = { /* TODO: inline expand or separate page */ },
        )

        Spacer(Modifier.height(10.dp))

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
            onClick = { /* TODO: wearable detail page */ },
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
                "当前为 Demo 模式，Profile 为预设默认值。正式版将支持语音修改偏好。",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = AuriNavy.copy(alpha = 0.6f),
            )
        }
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
