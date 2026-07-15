package com.pressureagent.mobile.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pressureagent.mobile.data.repository.ChatRepository
import com.pressureagent.mobile.data.repository.ChatStreamEvent
import com.pressureagent.mobile.domain.model.ChatMessage
import com.pressureagent.mobile.domain.model.ConfirmationStatus
import com.pressureagent.mobile.domain.model.ContentType
import com.pressureagent.mobile.domain.model.MessageRole
import com.pressureagent.mobile.domain.model.ToolCallInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceChatUiState())
    val uiState: StateFlow<VoiceChatUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    // ─── Public API ─────────────────────────────────────────────────────────

    /** Send a text message (typed or transcribed from voice). */
    fun sendMessage(text: String, inputMode: String = "text") {
        if (text.isBlank() || _uiState.value.isInputBlocked) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text.trim(),
            contentType = ContentType.TEXT,
            timestamp = now(),
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isProcessing = true,
                partialResponse = "",
                error = null,
            )
        }

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            var partialText = ""
            try {
                chatRepository.sendMessage(
                    message = text.trim(),
                    inputMode = inputMode,
                    sessionId = _uiState.value.sessionId,
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.TextDelta -> {
                            partialText += event.content
                            _uiState.update { it.copy(partialResponse = partialText) }
                        }
                        is ChatStreamEvent.ToolCallStarted -> {
                            // Commit any pending text before showing tool card
                            if (partialText.isNotBlank()) {
                                commitAiMessage(partialText)
                                partialText = ""
                            }
                            val toolMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.AI,
                                content = "",
                                contentType = ContentType.TOOL_CALL,
                                timestamp = now(),
                                toolCall = ToolCallInfo(
                                    toolCallId = event.toolCallId,
                                    functionName = event.functionName,
                                    arguments = event.arguments,
                                    success = null,
                                    summary = null,
                                ),
                            )
                            _uiState.update { it.copy(messages = it.messages + toolMsg, partialResponse = "") }
                        }
                        is ChatStreamEvent.ToolCallResult -> {
                            // Update the matching tool call card with result
                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages.map { msg ->
                                        if (msg.toolCall?.toolCallId == event.toolCallId) {
                                            msg.copy(
                                                toolCall = msg.toolCall!!.copy(
                                                    success = event.success,
                                                    summary = event.summary,
                                                ),
                                            )
                                        } else msg
                                    }
                                )
                            }
                        }
                        is ChatStreamEvent.ConfirmationRequired -> {
                            // Map to domain Confirmation for display
                            val domainConfirmation = com.pressureagent.mobile.domain.model.Confirmation(
                                id = event.confirmationId,
                                prompt = event.prompt,
                                status = ConfirmationStatus.PENDING,
                                actionIds = event.actionIds,
                            )
                            val confirmMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.AI,
                                content = "",
                                contentType = ContentType.CONFIRMATION,
                                timestamp = now(),
                                confirmation = domainConfirmation,
                            )
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + confirmMsg,
                                    pendingConfirmation = domainConfirmation,
                                    partialResponse = "",
                                )
                            }
                        }
                        is ChatStreamEvent.Done -> {
                            // Commit remaining partial text as final AI message
                            if (partialText.isNotBlank()) {
                                commitAiMessage(partialText)
                                partialText = ""
                            }
                            _uiState.update {
                                it.copy(
                                    sessionId = event.sessionId,
                                    isProcessing = false,
                                    partialResponse = "",
                                )
                            }
                        }
                        is ChatStreamEvent.Error -> {
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    partialResponse = "",
                                    error = event.message,
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        partialResponse = "",
                        error = e.message ?: "发送失败",
                    )
                }
            }
        }
    }

    /** Confirm a pending high-risk action. */
    fun confirm() {
        val confirmation = _uiState.value.pendingConfirmation ?: return
        val sessionId = _uiState.value.sessionId ?: return

        viewModelScope.launch {
            try {
                chatRepository.confirmAction(sessionId, confirmation.id, "accepted")

                // Update confirmation status in messages
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.confirmation?.id == confirmation.id) {
                                msg.copy(
                                    confirmation = msg.confirmation!!.copy(status = ConfirmationStatus.ACCEPTED),
                                )
                            } else msg
                        },
                        pendingConfirmation = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Reject a pending high-risk action. */
    fun reject() {
        val confirmation = _uiState.value.pendingConfirmation ?: return
        val sessionId = _uiState.value.sessionId ?: return

        viewModelScope.launch {
            try {
                chatRepository.confirmAction(sessionId, confirmation.id, "rejected")

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.confirmation?.id == confirmation.id) {
                                msg.copy(
                                    confirmation = msg.confirmation!!.copy(status = ConfirmationStatus.REJECTED),
                                )
                            } else msg
                        },
                        pendingConfirmation = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Set voice listening state. */
    fun setListening(isListening: Boolean) {
        _uiState.update { it.copy(isListening = isListening) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun commitAiMessage(text: String) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.AI,
            content = text,
            contentType = ContentType.TEXT,
            timestamp = now(),
        )
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    private fun now(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
