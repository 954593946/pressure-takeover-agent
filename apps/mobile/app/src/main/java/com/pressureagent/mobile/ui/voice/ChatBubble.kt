package com.pressureagent.mobile.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pressureagent.mobile.domain.model.ContentType
import com.pressureagent.mobile.domain.model.MessageRole

/**
 * A single chat message bubble.
 *
 * - [MessageRole.USER]: right-aligned, primary color background
 * - [MessageRole.AI]: left-aligned, surface color background
 * - [MessageRole.SYSTEM]: centered, muted text
 */
@Composable
fun ChatBubble(
    role: MessageRole,
    content: String,
    contentType: ContentType,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
) {
    when (role) {
        MessageRole.USER -> UserBubble(content, modifier)
        MessageRole.AI -> AiBubble(content, isStreaming, modifier)
        MessageRole.SYSTEM -> SystemBubble(content, modifier)
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun AiBubble(text: String, isStreaming: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isStreaming) {
                    Text(
                        text = "▌",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Streaming indicator shown while AI is generating a response.
 */
@Composable
fun StreamingBubble(
    partialText: String,
    modifier: Modifier = Modifier,
) {
    if (partialText.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "思考中...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    } else {
        AiBubble(partialText, isStreaming = true, modifier = modifier)
    }
}

/**
 * Empty state shown when no messages exist.
 *
 * Shows a welcome message with tappable suggestion chips
 * so users can discover what the chat can do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyChatState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = listOf(
        "📝" to "创建接孩子的任务",
        "📅" to "加个日程明天开会",
        "⏰" to "会议延迟了",
        "🚗" to "我出发了",
        "🚦" to "路上堵车了",
        "📨" to "帮我通知老师",
        "🆘" to "帮我处理",
        "❓" to "能做什么",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "🎙️",
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            text = "随行压力接管助手",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "直接说出你的需求，我会自动处理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "试试这些：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestionChip(onClick = { onSuggestionClick("创建接孩子的任务") }, label = { Text("📝 创建接孩子的任务", style = MaterialTheme.typography.labelMedium) })
            SuggestionChip(onClick = { onSuggestionClick("加个日程明天开会") }, label = { Text("📅 加个日程明天开会", style = MaterialTheme.typography.labelMedium) })
            SuggestionChip(onClick = { onSuggestionClick("会议延迟了") }, label = { Text("⏰ 会议延迟了", style = MaterialTheme.typography.labelMedium) })
            SuggestionChip(onClick = { onSuggestionClick("我出发了") }, label = { Text("🚗 我出发了", style = MaterialTheme.typography.labelMedium) })
            SuggestionChip(onClick = { onSuggestionClick("路上堵车了") }, label = { Text("🚦 路上堵车了", style = MaterialTheme.typography.labelMedium) })
            SuggestionChip(onClick = { onSuggestionClick("帮我通知老师") }, label = { Text("📨 帮我通知老师", style = MaterialTheme.typography.labelMedium) })
            SuggestionChip(onClick = { onSuggestionClick("帮我处理") }, label = { Text("🆘 帮我处理", style = MaterialTheme.typography.labelMedium) })
            SuggestionChip(onClick = { onSuggestionClick("能做什么") }, label = { Text("❓ 能做什么", style = MaterialTheme.typography.labelMedium) })
        }
    }
}
