package com.pressureagent.mobile.ui.voice

import com.pressureagent.mobile.domain.model.ChatMessage
import com.pressureagent.mobile.domain.model.Confirmation

/**
 * UI state for the voice chat screen.
 *
 * Separated from the ViewModel to keep state definition clean.
 */
data class VoiceChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessionId: String? = null,
    val isListening: Boolean = false,          // Voice recording in progress
    val isProcessing: Boolean = false,          // AI is thinking / streaming
    val partialResponse: String = "",           // Accumulated streaming text (typing effect)
    val pendingConfirmation: Confirmation? = null,  // Inline confirmation prompt
    val error: String? = null,
) {
    /** Whether there are any messages in the chat. */
    val hasMessages: Boolean get() = messages.isNotEmpty()

    /** Whether the UI is in a state that should disable input. */
    val isInputBlocked: Boolean get() = isProcessing || isListening
}
