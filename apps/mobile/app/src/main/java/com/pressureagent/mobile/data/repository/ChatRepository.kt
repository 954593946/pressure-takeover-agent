package com.pressureagent.mobile.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for the conversational AI chat.
 *
 * Sends user messages to the agent-api chat endpoint and receives
 * streaming responses (text deltas, tool calls, confirmation requests).
 */
interface ChatRepository {

    /** Send a message and receive a streaming response. Never completes — errors are emitted then recovered. */
    fun sendMessage(
        message: String,
        inputMode: String = "text",
        sessionId: String? = null,
    ): Flow<ChatStreamEvent>

    /** Confirm or reject a high-risk action in the chat context. */
    suspend fun confirmAction(
        sessionId: String,
        confirmationId: String,
        decision: String,
    )
}

// ─── Stream event types ────────────────────────────────────────────────────────

sealed class ChatStreamEvent {
    /** Partial text from the AI (typing effect). */
    data class TextDelta(val content: String) : ChatStreamEvent()

    /** The AI has started calling a tool. */
    data class ToolCallStarted(
        val toolCallId: String,
        val functionName: String,
        val arguments: String,
    ) : ChatStreamEvent()

    /** A tool call completed. */
    data class ToolCallResult(
        val toolCallId: String,
        val success: Boolean,
        val summary: String,
    ) : ChatStreamEvent()

    /** A high-risk action needs user confirmation. */
    data class ConfirmationRequired(
        val confirmationId: String,
        val prompt: String,
        val actionIds: List<String>,
    ) : ChatStreamEvent()

    /** The AI response is complete. */
    data class Done(
        val sessionId: String,
        val revision: Int,
    ) : ChatStreamEvent()

    /** An error occurred during processing. */
    data class Error(val message: String) : ChatStreamEvent()
}
