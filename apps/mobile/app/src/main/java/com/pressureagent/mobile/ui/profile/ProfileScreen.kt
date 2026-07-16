package com.pressureagent.mobile.ui.profile

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
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("个人偏好", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(4.dp))
        Text("Agent 根据你的偏好调整个性化策略", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        if (state.profile == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("等待数据…", color = Color.Gray)
            }
            return@Column
        }

        val p = state.profile!!

        // Profile type card
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("方案偏好", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileTypeCard(
                        label = "⚡ 效率优先",
                        desc = "最快完成、最低价格",
                        selected = p.profileType == ProfileType.EFFICIENCY,
                        modifier = Modifier.weight(1f),
                    )
                    ProfileTypeCard(
                        label = "✨ 品质优先",
                        desc = "品牌一致、质量优先",
                        selected = p.profileType == ProfileType.QUALITY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Detail settings
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("详细设置", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                Spacer(Modifier.height(12.dp))
                SettingsRow("交互风格", p.tone)
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsRow("语音主动阈值", when (p.proactiveVoiceThreshold) {
                    VoiceThreshold.L1 -> "L1 — 时间窗口压缩时主动询问"
                    VoiceThreshold.L2 -> "L2 — 协调接管时主动询问"
                    VoiceThreshold.L3 -> "L3 — 仅高负荷保护时询问"
                })
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsRow("触觉模式", when (p.hapticMode) {
                    HapticMode.CLEAR -> "明确 — 每次状态变化均震动"
                    HapticMode.GENTLE -> "柔和 — 仅关键变化震动"
                })
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsRow("预算上限", "¥%.0f".format(p.budgetLimit))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsRow("配送策略", when (p.deliveryPriority) {
                    DeliveryPriority.FASTEST -> "最快送达"
                    DeliveryPriority.QUALITY_FIRST -> "品质优先"
                })
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsRow("替代规则", when (p.substitutionPolicy) {
                    SubstitutionPolicy.SAME_SPEC_WITHIN_BUDGET -> "同规格 + 预算内"
                    SubstitutionPolicy.SAME_BRAND_ONLY -> "同品牌限定"
                })
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsRow("解释深度", when (p.explanationDepth) {
                    ExplanationDepth.BRIEF -> "简洁 — 结论 + 行动"
                    ExplanationDepth.DETAILED -> "详细 — 含推理过程"
                })
            }
        }

        Spacer(Modifier.height(12.dp))

        // Demo note
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AuriWarning.copy(alpha = 0.08f))) {
            Text(
                "当前为 Demo 模式，Profile 为预设默认值。正式版将支持语音修改偏好。",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = AuriNavy.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileTypeCard(label: String, desc: String, selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AuriNavy.copy(alpha = 0.06f) else Color(0xFFF8F8F8),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = if (selected) AuriNavy else Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (selected) {
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = AuriGold) {
                    Text("当前", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = AuriNavy)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AuriNavy)
    }
}
