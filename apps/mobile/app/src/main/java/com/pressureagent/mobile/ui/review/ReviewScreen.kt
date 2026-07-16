package com.pressureagent.mobile.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

@Composable
fun ReviewScreen(viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("停车复盘", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(4.dp))
        Text("回顾 AURI 在驾驶过程中为你处理了什么", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        if (state.ledger.isEmpty() && state.completedActions.isEmpty() && state.completedOrders.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("暂无复盘数据", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text("驾驶过程中 Agent 执行的操作会记录在此", style = MaterialTheme.typography.bodySmall, color = Color.Gray.copy(alpha = 0.7f))
                }
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Summary card
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AuriNavy)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("本次驾驶摘要", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        SummaryStat("已处理任务", "${state.completedActions.size + state.completedOrders.size} 项")
                        if (state.completedOrders.isNotEmpty()) {
                            SummaryStat("服务订单", "${state.completedOrders.size} 笔 ¥%.1f".format(state.completedOrders.sumOf { it.total }))
                        }
                        SummaryStat("消息发送", "${state.completedActions.count { it.type == ActionType.MESSAGE }} 条 (模拟)")
                        SummaryStat("任务重排", "${state.completedActions.count { it.type == ActionType.RESCHEDULE }} 项")
                    }
                }
            }

            // Timeline
            if (state.ledger.isNotEmpty()) {
                item {
                    Text("时间线", fontWeight = FontWeight.SemiBold, color = AuriNavy, style = MaterialTheme.typography.titleSmall)
                }

                itemsIndexed(state.ledger) { index, entry ->
                    TimelineItem(index = index + 1, total = state.ledger.size, text = entry)
                }
            }

            // Completed service orders
            if (state.completedOrders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("服务订单", fontWeight = FontWeight.SemiBold, color = AuriNavy, style = MaterialTheme.typography.titleSmall)
                }
                state.completedOrders.forEach { order ->
                    item {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("🛒 超市配送 — ${order.items.size} 项", fontWeight = FontWeight.SemiBold, color = AuriNavy)
                                Spacer(Modifier.height(4.dp))
                                Text("¥%.1f · 配送 ${order.deliveryWindow}".format(order.total), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text("订单号: ${order.orderId ?: "—"} (模拟)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = AuriGold, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimelineItem(index: Int, total: Int, text: String) {
    val isLast = index == total
    val dotColor = when {
        text.contains("L2") || text.contains("L3") -> AuriWarning
        text.contains("已完成") || text.contains("已确认") || text.contains("已发送") || text.contains("已下单") -> AuriSuccess
        text.contains("交接") -> AuriProcessing
        else -> AuriNavy.copy(alpha = 0.4f)
    }

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Timeline dot + line
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
            Box(
                modifier = Modifier.size(12.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(10.dp)) {}
            }
            if (!isLast) {
                Box(
                    modifier = Modifier.width(2.dp).weight(1f).padding(vertical = 2.dp),
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFE0E0E0)) {}
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 8.dp),
        ) {
            Text(
                text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AuriNavy,
            )
        }
    }
}
