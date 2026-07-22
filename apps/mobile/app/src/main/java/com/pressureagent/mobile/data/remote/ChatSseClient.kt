package com.pressureagent.mobile.data.remote

import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.data.repository.ChatStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import java.util.concurrent.TimeUnit

/**
 * Request body for the streaming chat endpoint.
 */
@Serializable
private data class ChatStreamRequest(
    val message: String,
    val inputMode: String = "text",
    val sessionId: String? = null,
)

/**
 * SSE client for the streaming chat endpoint: POST /v1/chat.
 *
 * Sends a chat message via POST and reads the SSE response stream.
 * The stream contains [ChatStreamEvent] variants serialized as JSON.
 *
 * Stream format (each `data:` line is a JSON object):
 * ```
 * data: {"type":"text_delta","content":"好的"}
 * data: {"type":"tool_call","toolCallId":"...","function":{"name":"create_task","arguments":{...}}}
 * data: {"type":"done","sessionId":"...","revision":5}
 * ```
 */
class ChatSseClient(
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val token: String = "",
) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@ChatSseClient.json)
        }
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(120, TimeUnit.SECONDS) // finite timeout to prevent thread leak on cancel
                retryOnConnectionFailure(true)
            }
        }
    }

    /**
     * Sends [message] to the chat endpoint and returns a [Flow] of [ChatStreamEvent].
     * The flow completes when the stream ends or an error occurs.
     */
    fun streamChat(
        message: String,
        inputMode: String = "text",
        sessionId: String? = null,
    ): Flow<ChatStreamEvent> = callbackFlow {
        AppLogger.i("ChatSSE", "POST /v1/chat msg='${message.take(50)}' session=$sessionId token=${if (token.isNotBlank()) "yes" else "no"}")
        try {
            val requestBody = ChatStreamRequest(
                message = message,
                inputMode = inputMode,
                sessionId = sessionId,
            )
            client.post("$baseUrl/v1/chat") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Accept", "text/event-stream")
                    if (token.isNotBlank()) {
                        append("X-Agent-Token", token)
                    }
                }
                setBody(json.encodeToString(requestBody))
            }.let { response ->
                AppLogger.i("ChatSSE", "Response status=${response.status.value}")
                if (response.status.value !in 200..299) {
                    AppLogger.e("ChatSSE", "Non-2xx status: ${response.status.value}")
                    trySend(ChatStreamEvent.Error("Chat endpoint returned ${response.status.value}"))
                    return@callbackFlow
                }
                val channel = response.bodyAsChannel()
                var eventCount = 0
                while (isActive && !channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: continue
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data.isNotEmpty()) {
                            val event = parseEvent(data)
                            if (event != null) {
                                eventCount++
                                trySend(event)
                            }
                        }
                    }
                }
                AppLogger.i("ChatSSE", "Stream ended, events=$eventCount")
            }
        } catch (e: Exception) {
            AppLogger.e("ChatSSE", "Connection failed", e)
            trySend(ChatStreamEvent.Error(e.message ?: "Connection error"))
        }
        awaitClose { AppLogger.d("ChatSSE", "Flow closed") }
    }

    private fun parseEvent(data: String): ChatStreamEvent? {
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text_delta" -> {
                    val content = obj["content"]?.jsonPrimitive?.content ?: ""
                    ChatStreamEvent.TextDelta(content)
                }
                "tool_call" -> {
                    val toolCallId = obj["toolCallId"]?.jsonPrimitive?.content ?: ""
                    val func = obj["function"]?.jsonObject
                    val name = func?.get("name")?.jsonPrimitive?.content ?: ""
                    val args = func?.get("arguments")?.toString() ?: "{}"
                    ChatStreamEvent.ToolCallStarted(toolCallId, name, args)
                }
                "tool_result" -> {
                    val toolCallId = obj["toolCallId"]?.jsonPrimitive?.content ?: ""
                    val success = obj["success"]?.jsonPrimitive?.boolean ?: false
                    val summary = obj["summary"]?.jsonPrimitive?.content ?: ""
                    ChatStreamEvent.ToolCallResult(toolCallId, success, summary)
                }
                "confirmation_required" -> {
                    val confirmationId = obj["confirmationId"]?.jsonPrimitive?.content ?: ""
                    val prompt = obj["prompt"]?.jsonPrimitive?.content ?: ""
                    val actionIds = obj["actionIds"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content } ?: emptyList()
                    ChatStreamEvent.ConfirmationRequired(confirmationId, prompt, actionIds)
                }
                "done" -> {
                    val sessionId = obj["sessionId"]?.jsonPrimitive?.content ?: ""
                    val revision = obj["revision"]?.jsonPrimitive?.int ?: 0
                    ChatStreamEvent.Done(sessionId, revision)
                }
                "error" -> {
                    val message = obj["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    ChatStreamEvent.Error(message)
                }
                else -> null
            }
        } catch (_: Exception) {
            null // skip malformed frames
        }
    }
}
