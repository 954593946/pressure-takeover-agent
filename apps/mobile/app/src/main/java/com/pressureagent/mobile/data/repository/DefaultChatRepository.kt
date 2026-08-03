package com.pressureagent.mobile.data.repository

import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.data.remote.ChatApiService
import com.pressureagent.mobile.data.remote.ChatRequest
import com.pressureagent.mobile.data.remote.ChatSseClient
import com.pressureagent.mobile.data.remote.ConfirmRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import retrofit2.HttpException
import java.io.Closeable
import java.io.IOException
import java.util.UUID
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
        val clientEventId = "evt_chat_${UUID.randomUUID()}"
        // Primary: SSE streaming
        return sseClient.streamChat(
            message = message,
            inputMode = inputMode,
            sessionId = sessionId,
            clientEventId = clientEventId,
        ).catch { e ->
            if (e is CancellationException) throw e
            AppLogger.w("ChatRepo", "SSE failed, trying non-streaming fallback: ${e.javaClass.simpleName}: ${e.message}")
            // Fallback: non-streaming response
            try {
                val response = api.sendMessage(
                    ChatRequest(
                        message = message,
                        inputMode = inputMode,
                        sessionId = sessionId,
                        clientEventId = clientEventId,
                    )
                )
                AppLogger.i("ChatRepo", "Non-streaming fallback OK, response=${response.responseText.take(50)}")
                emit(ChatStreamEvent.TextDelta(response.responseText))
                emit(ChatStreamEvent.Done(response.sessionId, response.revision))
            } catch (fallbackError: CancellationException) {
                throw fallbackError
            } catch (fallbackError: Exception) {
                AppLogger.e("ChatRepo", "Non-streaming fallback also failed", fallbackError)
                emit(fallbackError.toChatError())
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

internal fun Throwable.toChatError(): ChatStreamEvent.Error = when (this) {
    is HttpException -> when (code()) {
        400, 422 -> ChatStreamEvent.Error("CHAT_REQUEST_INVALID", "请求内容无法处理", false)
        401, 403 -> ChatStreamEvent.Error("CHAT_AUTH_REQUIRED", "Agent 鉴权失败，请检查访问配置", false)
        404 -> ChatStreamEvent.Error("CHAT_ENDPOINT_NOT_FOUND", "Agent Chat 接口不可用", false)
        409 -> ChatStreamEvent.Error("CHAT_CONFLICT", "请求状态已变化，请同步后重试", false)
        429 -> ChatStreamEvent.Error("CHAT_RATE_LIMITED", "请求过于频繁，请稍后重试", true)
        in 500..599 -> ChatStreamEvent.Error("CHAT_UPSTREAM_UNAVAILABLE", "Agent 服务暂时不可用", true)
        else -> ChatStreamEvent.Error("CHAT_HTTP_ERROR", "Agent 请求失败（HTTP ${code()}）", false)
    }
    is IOException -> ChatStreamEvent.Error("CHAT_TRANSPORT_UNAVAILABLE", "无法连接 Agent 服务", true)
    else -> ChatStreamEvent.Error("CHAT_CLIENT_ERROR", message ?: "Chat 处理失败", false)
}
