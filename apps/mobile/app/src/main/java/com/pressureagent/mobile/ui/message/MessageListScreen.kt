package com.pressureagent.mobile.ui.message

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*
import com.pressureagent.mobile.ui.theme.*

@Composable
fun MessageListScreen(viewModel: MessageListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("消息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuriNavy)
        Spacer(Modifier.height(12.dp))

        if (state.messageActions.isEmpty() && state.confirmation == null && state.completedMessages.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("暂无消息", color = Color.Gray)
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Active message actions (awaiting confirmation or planned)
            items(state.messageActions, key = { it.actionId }) { action ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(action.target, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriNavy)
                            Surface(shape = RoundedCornerShape(6.dp), color = when (action.status) {
                                ActionStatus.AWAITING_CONFIRMATION -> AuriWarning.copy(alpha = 0.15f)
                                else -> Color(0xFFF0F0F0)
                            }) {
                                Text(
                                    when (action.status) {
                                        ActionStatus.AWAITING_CONFIRMATION -> "待确认"
                                        ActionStatus.PLANNED -> "已规划"
                                        ActionStatus.COMPLETED -> "已发送(模拟)"
                                        else -> action.status.name
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(action.summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        action.detailsRef?.let { body ->
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = AuriIvory) {
                                Text("「$body」", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = AuriNavy.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // Completed messages
            items(state.completedMessages, key = { it.actionId }) { action ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(action.target, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AuriNavy.copy(alpha = 0.6f))
                            Surface(shape = RoundedCornerShape(6.dp), color = AuriSuccess.copy(alpha = 0.1f)) {
                                Text("已发送(模拟)", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = AuriSuccess)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(action.summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            // Confirmation
            if (state.confirmation != null && state.confirmation!!.status == ConfirmationStatus.PENDING) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AuriNavy)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("📋 ${state.outputConclusion}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = viewModel::reject, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("拒绝") }
                                Button(onClick = viewModel::confirm, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AuriGold)) { Text("确认发送", color = AuriNavy, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }

            // Resolved confirmation status
            if (state.confirmation != null && state.confirmation!!.status != ConfirmationStatus.PENDING) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = when (state.confirmation!!.status) {
                        ConfirmationStatus.ACCEPTED -> AuriSuccess.copy(alpha = 0.1f)
                        ConfirmationStatus.REJECTED -> AuriCritical.copy(alpha = 0.1f)
                        else -> Color(0xFFF0F0F0)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when (state.confirmation!!.status) {
                                ConfirmationStatus.ACCEPTED -> "✅ 已确认发送"
                                ConfirmationStatus.REJECTED -> "❌ 已拒绝"
                                ConfirmationStatus.EXPIRED -> "⏰ 已过期"
                                ConfirmationStatus.PENDING -> ""
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
