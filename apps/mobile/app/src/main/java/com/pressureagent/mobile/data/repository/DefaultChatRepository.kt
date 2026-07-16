package com.pressureagent.mobile.data.repository

import com.pressureagent.mobile.data.remote.ChatApiService
import com.pressureagent.mobile.data.remote.ChatRequest
import com.pressureagent.mobile.data.remote.ChatSseClient
import com.pressureagent.mobile.data.remote.ConfirmRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [ChatRepository].
 *
 * Strategy:
 * 1. Try SSE streaming via [ChatSseClient] (primary path).
 * 2. If SSE is unavailable, fall back to [ChatApiService.sendMessage] (non-streaming).
 * 3. Confirmation goes through [ChatApiService.confirmAction].
 */
@Singleton
class DefaultChatRepository @Inject constructor(
    private val api: ChatApiService,
    private val sseClient: ChatSseClient,
) : ChatRepository, Closeable {

    override fun sendMessage(
        message: String,
        inputMode: String,
        sessionId: String?,
    ): Flow<ChatStreamEvent> {
        // Primary: SSE streaming
        return sseClient.streamChat(
            message = message,
            inputMode = inputMode,
            sessionId = sessionId,
        ).catch { e ->
            // Fallback: non-streaming response
            try {
                val response = api.sendMessage(
                    ChatRequest(
                        message = message,
                        inputMode = inputMode,
                        sessionId = sessionId,
                    )
                )
                emit(ChatStreamEvent.TextDelta(response.responseText))
                emit(ChatStreamEvent.Done(response.sessionId, response.revision))
            } catch (fallbackError: Exception) {
                emit(ChatStreamEvent.Error(fallbackError.message ?: "Chat unavailable"))
            }
        }
    }

    override suspend fun confirmAction(
        sessionId: String,
        confirmationId: String,
        decision: String,
    ) {
        api.confirmAction(
            ConfirmRequest(
                sessionId = sessionId,
                confirmationId = confirmationId,
                decision = decision,
            )
        )
    }

    override fun close() {
        // SSE client is closed when its flow is cancelled
    }
}
