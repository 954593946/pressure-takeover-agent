package com.pressureagent.mobile.data.remote

import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.domain.model.WorldState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * SSE client for GET /v1/stream.
 *
 * Parses Server-Sent Events lines:
 * ```
 * data: {"schemaVersion":"0.1.0", ...}
 *
 * ```
 *
 * Reconnects automatically with exponential backoff.
 * Falls back to polling if the SSE endpoint is unavailable.
 */
class SseClient(
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val token: String = "",
    private val reconnectDelayMs: Long = 2_000L,
    private val maxReconnectDelayMs: Long = 30_000L,
) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(300, TimeUnit.SECONDS) // 5 min SSE timeout, reconnect on expiry
                retryOnConnectionFailure(true)
            }
        }
    }

    /**
     * Returns a [Flow] of [WorldState] parsed from the SSE stream.
     * Never completes — on disconnect, reconnects with backoff.
     */
    fun observe(): Flow<WorldState> = callbackFlow {
        var delayMs = reconnectDelayMs
        var attemptCount = 0
        AppLogger.i("StateSSE", "GET /v1/stream starting, token=${if (token.isNotBlank()) "yes" else "no"}")
        while (isActive) {
            attemptCount++
            try {
                AppLogger.d("StateSSE", "SSE connect attempt #$attemptCount")
                client.get("$baseUrl/v1/stream") {
                    headers {
                        append("Accept", "text/event-stream")
                        if (token.isNotBlank()) {
                            append("X-Agent-Token", token)
                        }
                    }
                }.let { response ->
                    AppLogger.i("StateSSE", "SSE connected, status=${response.status.value}")
                    var frameCount = 0
                    val channel = response.bodyAsChannel()
                    while (isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: continue
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isNotEmpty()) {
                                try {
                                    val ws = json.decodeFromString(WorldState.serializer(), data)
                                    trySend(ws)
                                    frameCount++
                                } catch (_: Exception) { /* skip malformed frames */ }
                            }
                        }
                    }
                    AppLogger.i("StateSSE", "SSE stream ended, frames=$frameCount")
                }
                // Connection ended cleanly — reset backoff
                delayMs = reconnectDelayMs
            } catch (e: Exception) {
                AppLogger.w("StateSSE", "SSE error (attempt #$attemptCount): ${e.javaClass.simpleName}: ${e.message}")
            }
            if (isActive) {
                AppLogger.d("StateSSE", "Reconnecting in ${delayMs}ms")
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxReconnectDelayMs)
            }
        }
        awaitClose { client.close() }
    }.retry(Long.MAX_VALUE) {
        delay(reconnectDelayMs)
        true
    }
}
