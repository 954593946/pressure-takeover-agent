package com.pressureagent.mobile.ui.message

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.*

@Composable
fun MessageListScreen(viewModel: MessageListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("消息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state.messages.isEmpty() && state.confirmation == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("暂无消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.messages, key = { it.id }) { msg ->
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = when (msg.status) {
                        MessageStatus.SIMULATED_SENT -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    })
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${if (msg.audience == Audience.TEACHER) "👩‍🏫" else "👨‍👩‍👧"} ${msg.displayName ?: ""}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Surface(shape = MaterialTheme.shapes.extraSmall, color = when (msg.status) {
                                MessageStatus.SIMULATED_SENT -> MaterialTheme.colorScheme.primaryContainer
                                MessageStatus.AWAITING_CONFIRMATION -> MaterialTheme.colorScheme.tertiaryContainer
                                MessageStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant
                            }) {
                                Text(
                                    when (msg.status) {
                                        MessageStatus.DRAFT -> "草稿"
                                        MessageStatus.AWAITING_CONFIRMATION -> "待确认"
                                        MessageStatus.SIMULATED_SENT -> "已发送"
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (state.confirmation != null && state.confirmation!!.status == ConfirmationStatus.PENDING) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📋 ${state.confirmation!!.prompt}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = viewModel::reject, modifier = Modifier.weight(1f)) { Text("拒绝") }
                                Button(onClick = viewModel::confirm, modifier = Modifier.weight(1f)) { Text("确认发送") }
                            }
                        }
                    }
                }
            }

            if (state.confirmation != null && state.confirmation!!.status != ConfirmationStatus.PENDING) {
                item {
                    Surface(shape = MaterialTheme.shapes.small, color = when (state.confirmation!!.status) {
                        ConfirmationStatus.ACCEPTED -> MaterialTheme.colorScheme.primaryContainer
                        ConfirmationStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
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
