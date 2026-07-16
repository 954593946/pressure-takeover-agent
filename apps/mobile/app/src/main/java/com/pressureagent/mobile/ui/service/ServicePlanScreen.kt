package com.pressureagent.mobile.ui.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.BudgetStatus
import com.pressureagent.mobile.domain.model.ServiceItem
import com.pressureagent.mobile.domain.model.ServiceOrder
import com.pressureagent.mobile.domain.model.ServiceOrderStatus
import com.pressureagent.mobile.ui.theme.*

@Composable
fun ServicePlanScreen(viewModel: ServicePlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("服务方案", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(4.dp))
        Text("Agent 根据你的任务、偏好和预算自动生成", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        if (state.orders.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛒", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("暂无服务方案", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text("当 Agent 检测到弹性任务可被服务替代时\n会自动生成服务方案", style = MaterialTheme.typography.bodySmall, color = Color.Gray.copy(alpha = 0.7f))
                }
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(state.orders, key = { it.previewId }) { order ->
                OrderDetailCard(order = order)
            }
        }
    }
}

@Composable
private fun OrderDetailCard(order: ServiceOrder) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🛒 超市配送", fontWeight = FontWeight.Bold, color = AuriNavy, style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(8.dp), color = when (order.status) {
                    ServiceOrderStatus.PREVIEW -> AuriProcessing.copy(alpha = 0.1f)
                    ServiceOrderStatus.AWAITING_CONFIRMATION -> AuriWarning.copy(alpha = 0.15f)
                    ServiceOrderStatus.SUBMITTED -> AuriSuccess.copy(alpha = 0.1f)
                    ServiceOrderStatus.BLOCKED -> AuriCritical.copy(alpha = 0.1f)
                    ServiceOrderStatus.FAILED -> AuriCritical.copy(alpha = 0.15f)
                }) {
                    Text(
                        when (order.status) {
                            ServiceOrderStatus.PREVIEW -> "预览"
                            ServiceOrderStatus.AWAITING_CONFIRMATION -> "待确认"
                            ServiceOrderStatus.SUBMITTED -> "已下单(模拟)"
                            ServiceOrderStatus.BLOCKED -> "已阻止"
                            ServiceOrderStatus.FAILED -> "失败"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when (order.status) {
                            ServiceOrderStatus.PREVIEW -> AuriProcessing
                            ServiceOrderStatus.AWAITING_CONFIRMATION -> AuriWarning
                            ServiceOrderStatus.SUBMITTED -> AuriSuccess
                            else -> AuriCritical
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Items
            order.items.forEach { item ->
                OrderItemRow(item = item)
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(Modifier.height(12.dp))

            // Summary
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("合计 ${order.items.size} 项", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Row {
                        Text("配送: ${order.deliveryWindow}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) AuriSuccess.copy(alpha = 0.1f) else AuriCritical.copy(alpha = 0.1f)) {
                            Text(
                                if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) "预算内" else "超预算",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (order.budgetStatus == BudgetStatus.WITHIN_BUDGET) AuriSuccess else AuriCritical,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("¥%.1f".format(order.total), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AuriNavy)
                    Text("预算 ¥%.0f".format(order.budgetLimit), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            // Order ID when submitted
            order.orderId?.let { oid ->
                Spacer(Modifier.height(8.dp))
                Text("订单号: $oid (模拟)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            // Error
            order.errorCode?.let { code ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = AuriCritical.copy(alpha = 0.1f)) {
                    Text(
                        "⚠ ${errorLabel(code)}",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = AuriCritical,
                    )
                }
            }
        }
    }

    // Demo note
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AuriWarning.copy(alpha = 0.08f))) {
        Text(
            "商品、库存、价格、配送均为模拟 Demo 数据，不代表真实可购买商品。",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = AuriNavy.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun OrderItemRow(item: ServiceItem) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium, color = AuriNavy)
            Row {
                Text("×${item.quantity}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                item.substitution?.let { sub ->
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = AuriGold.copy(alpha = 0.12f)) {
                        Text(
                            "替代: $sub",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuriGold,
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("¥%.1f".format(item.unitPrice), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("¥%.1f".format(item.subtotal), fontWeight = FontWeight.Medium, color = AuriNavy)
        }
    }
}

private fun errorLabel(code: String): String = when (code) {
    "OUT_OF_STOCK" -> "商品缺货"
    "OVER_BUDGET" -> "超出预算"
    "DUPLICATE" -> "重复订单"
    "EXPIRED" -> "已过期"
    "NOT_AUTHORIZED" -> "未授权"
    "NOT_FOUND" -> "未找到"
    else -> code
}
