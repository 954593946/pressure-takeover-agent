package com.pressureagent.mobile.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pressureagent.mobile.domain.model.ToolCallInfo

/**
 * Card showing an AI tool call in the chat.
 *
 * Displays:
 * - Tool icon + function name
 * - Arguments (collapsed by default)
 * - Execution status (pending spinner, success check, or error indicator)
 * - Result summary (when completed)
 */
@Composable
fun ToolCallCard(
    toolCall: ToolCallInfo,
    modifier: Modifier = Modifier,
) {
    val isCompleted = toolCall.success != null
    val isSuccess = toolCall.success == true
    val icon = when (toolCall.functionName) {
        "create_task" -> "📝"
        "create_calendar_event" -> "📅"
        "report_meeting_delay" -> "⏰"
        "report_vehicle_mode" -> "🚗"
        "report_traffic" -> "🚦"
        "request_assistance" -> "🆘"
        "draft_message" -> "📨"
        "send_message" -> "✉️"
        "get_status" -> "🔍"
        "send_confirmation" -> "✅"
        "reset_demo" -> "🔄"
        else -> "🔧"
    }

    val label = when (toolCall.functionName) {
        "create_task" -> "创建任务"
        "create_calendar_event" -> "添加到日历"
        "report_meeting_delay" -> "报告延迟"
        "report_vehicle_mode" -> "切换车辆模式"
        "report_traffic" -> "路况更新"
        "request_assistance" -> "请求接管"
        "draft_message" -> "生成消息"
        "send_message" -> "发送消息"
        "get_status" -> "查询状态"
        "send_confirmation" -> "确认操作"
        "reset_demo" -> "重置演示"
        else -> toolCall.functionName
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                when {
                    !isCompleted -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    isSuccess -> Text("✅", style = MaterialTheme.typography.bodySmall)
                    else -> Text("❌", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (isCompleted && toolCall.summary != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = toolCall.summary!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Inline confirmation card inside the chat stream.
 *
 * Shows the prompt text and two buttons: confirm / reject.
 * The confirm button is disabled after first tap.
 */
@Composable
fun ConfirmationCard(
    prompt: String,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    isConfirmed: Boolean = false,
    isRejected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = !isConfirmed && !isRejected) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📋", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("拒绝")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("确认发送")
                    }
                }
            }
        }
    }

    // Show result after decision
    if (isConfirmed) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "✅ 已确认发送",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    if (isRejected) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = "❌ 已拒绝",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
