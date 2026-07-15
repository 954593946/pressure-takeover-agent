package com.pressureagent.mobile.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pressureagent.mobile.domain.model.ContentType
import com.pressureagent.mobile.domain.model.MessageRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: VoiceChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    var isTextMode by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size, state.partialResponse) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI 助手", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (state.isProcessing) {
                            Text(
                                "正在回复...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                text = textInput,
                onTextChange = { textInput = it },
                isTextMode = isTextMode,
                onToggleMode = { isTextMode = !isTextMode },
                isInputBlocked = state.isInputBlocked,
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendMessage(textInput.trim())
                        textInput = ""
                    }
                },
                onStartVoice = { viewModel.setListening(true) },
                onStopVoice = {
                    viewModel.setListening(false)
                    // In mock mode: simulate voice recognition with a demo message
                },
            )
        },
        snackbarHost = {
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::dismissError) { Text("关闭") }
                    },
                ) {
                    Text(error)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (!state.hasMessages && !state.isProcessing) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyChatState(
                        onSuggestionClick = { label ->
                            // Strip leading emoji (first 1-2 chars) + space
                            val cleanText = label.split(" ", limit = 2).getOrElse(1) { label }
                            viewModel.sendMessage(cleanText)
                        },
                    )
                }
            } else {
                // Message list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        when (message.contentType) {
                            ContentType.TEXT -> {
                                ChatBubble(
                                    role = message.role,
                                    content = message.content,
                                    contentType = message.contentType,
                                )
                            }
                            ContentType.TOOL_CALL -> {
                                message.toolCall?.let { toolCall ->
                                    Column {
                                        if (message.content.isNotBlank()) {
                                            ChatBubble(
                                                role = MessageRole.AI,
                                                content = message.content,
                                                contentType = ContentType.TEXT,
                                            )
                                        }
                                        ToolCallCard(toolCall = toolCall)
                                    }
                                }
                            }
                            ContentType.TOOL_RESULT -> {
                                message.toolCall?.let { toolCall ->
                                    ToolCallCard(toolCall = toolCall)
                                }
                            }
                            ContentType.CONFIRMATION -> {
                                message.confirmation?.let { conf ->
                                    ConfirmationCard(
                                        prompt = conf.prompt,
                                        onConfirm = viewModel::confirm,
                                        onReject = viewModel::reject,
                                        isConfirmed = conf.status == com.pressureagent.mobile.domain.model.ConfirmationStatus.ACCEPTED,
                                        isRejected = conf.status == com.pressureagent.mobile.domain.model.ConfirmationStatus.REJECTED,
                                    )
                                }
                            }
                        }
                    }

                    // Streaming partial response
                    if (state.isProcessing && state.partialResponse.isNotEmpty()) {
                        item(key = "streaming") {
                            StreamingBubble(partialText = state.partialResponse)
                        }
                    }

                    // Thinking indicator (processing but no partial text yet)
                    if (state.isProcessing && state.partialResponse.isEmpty()) {
                        item(key = "thinking") {
                            StreamingBubble(partialText = "")
                        }
                    }

                    // Spacer at bottom for comfortable scrolling
                    item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isTextMode: Boolean,
    onToggleMode: () -> Unit,
    isInputBlocked: Boolean,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isTextMode) {
                // Text input mode
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("输入消息…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isInputBlocked,
                    shape = RoundedCornerShape(24.dp),
                )

                // Toggle to voice
                IconButton(onClick = onToggleMode) {
                    Icon(Icons.Filled.Mic, contentDescription = "语音输入")
                }

                // Send button
                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !isInputBlocked,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            } else {
                // Voice input mode (default)
                // Toggle to text
                IconButton(onClick = onToggleMode) {
                    Icon(Icons.Filled.Keyboard, contentDescription = "键盘输入")
                }

                // Voice button (large, centered)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            // In mock mode: simulate voice input
                            onStartVoice()
                            onStopVoice()
                        },
                        enabled = !isInputBlocked,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "按住说话",
                            modifier = Modifier.size(32.dp),
                            tint = if (isInputBlocked)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.width(40.dp)) // Balance the keyboard button on the left
            }
        }
    }
}
