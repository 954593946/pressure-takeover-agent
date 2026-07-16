package com.pressureagent.mobile.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the chat endpoints.
 */
interface ChatApiService {

    /** Submit a chat message (non-streaming fallback). Returns the full response. */
    @POST("v1/chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    /** Confirm or reject a pending action. */
    @POST("v1/chat/confirm")
    suspend fun confirmAction(@Body request: ConfirmRequest): ConfirmResponse
}

// ─── Request / Response types ──────────────────────────────────────────────────

@kotlinx.serialization.Serializable
data class ChatRequest(
    val message: String,
    val inputMode: String = "text",
    val sessionId: String? = null,
)

@kotlinx.serialization.Serializable
data class ChatResponse(
    val sessionId: String,
    val responseText: String,
    val revision: Int,
)

@kotlinx.serialization.Serializable
data class ConfirmRequest(
    val sessionId: String,
    val confirmationId: String,
    val decision: String,
)

@kotlinx.serialization.Serializable
data class ConfirmResponse(
    val accepted: Boolean,
    val revision: Int,
)
