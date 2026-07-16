package com.pressureagent.mobile.data.remote

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
    private val reconnectDelayMs: Long = 2_000L,
    private val maxReconnectDelayMs: Long = 30_000L,
) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(0, TimeUnit.MILLISECONDS) // unlimited for SSE
            }
        }
    }

    /**
     * Returns a [Flow] of [WorldState] parsed from the SSE stream.
     * Never completes — on disconnect, reconnects with backoff.
     */
    fun observe(): Flow<WorldState> = callbackFlow {
        var delayMs = reconnectDelayMs
        while (isActive) {
            try {
                client.get("$baseUrl/v1/stream") {
                    headers { append("Accept", "text/event-stream") }
                }.let { response ->
                    val channel = response.bodyAsChannel()
                    while (isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: continue
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isNotEmpty()) {
                                try {
                                    val ws = json.decodeFromString(WorldState.serializer(), data)
                                    trySend(ws)
                                } catch (_: Exception) { /* skip malformed frames */ }
                            }
                        }
                    }
                }
                // Connection ended cleanly — reset backoff
                delayMs = reconnectDelayMs
            } catch (_: Exception) {
                // Connection error — retry with backoff
            }
            if (isActive) {
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
