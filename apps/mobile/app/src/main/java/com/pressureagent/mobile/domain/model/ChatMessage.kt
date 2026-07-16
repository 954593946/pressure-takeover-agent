package com.pressureagent.mobile.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ─── Message role ──────────────────────────────────────────────────────────────

@Serializable
enum class MessageRole {
    @SerialName("user") USER,
    @SerialName("ai") AI,
    @SerialName("system") SYSTEM,
}

// ─── Content type ──────────────────────────────────────────────────────────────

@Serializable
enum class ContentType {
    @SerialName("text") TEXT,
    @SerialName("tool_call") TOOL_CALL,
    @SerialName("tool_result") TOOL_RESULT,
    @SerialName("confirmation") CONFIRMATION,
}

// ─── Tool call info ────────────────────────────────────────────────────────────

@Serializable
data class ToolCallInfo(
    val toolCallId: String,
    val functionName: String,
    val arguments: String,          // JSON string (display-friendly)
    val success: Boolean? = null,
    val summary: String? = null,
)

// ─── Chat message ──────────────────────────────────────────────────────────────

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val contentType: ContentType,
    val timestamp: String,
    val toolCall: ToolCallInfo? = null,
    val confirmation: Confirmation? = null,
)
