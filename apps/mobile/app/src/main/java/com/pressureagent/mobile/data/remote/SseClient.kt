package com.pressureagent.mobile.data.remote

import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.domain.model.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * SSE client for GET /v1/stream.
 *
 * Uses OkHttp **directly** (not through Ktor) for SSE streaming. Ktor's
 * [io.ktor.client.statement.HttpResponse.bodyAsChannel] can buffer the
 * entire response body, which never completes for an infinite SSE stream.
 * OkHttp's native [okhttp3.ResponseBody.source] is genuinely streaming —
 * each line becomes available as soon as the server writes it.
 *
 * Reconnects automatically with exponential backoff + jitter.
 * SSE comment lines (": heartbeat") are treated as keepalive signals.
 *
 * @param sharedOkHttpClient The application-wide OkHttpClient (with
 *   MockAgent, auth, logging interceptors). A derived client with a 300s
 *   read timeout is created for SSE.
 */
class SseClient(
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val token: String = "",
    private val reconnectDelayMs: Long = 2_000L,
    private val maxReconnectDelayMs: Long = 30_000L,
    sharedOkHttpClient: OkHttpClient? = null,
) {

    // SSE-specific OkHttpClient: inherits all interceptors from the shared
    // client, but uses a 300s read timeout suitable for long-lived SSE.
    private val okHttp: OkHttpClient = (sharedOkHttpClient ?: OkHttpClient()).newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Returns a [Flow] of [WorldState] parsed from the SSE stream.
     *
     * Never completes under normal operation — on disconnect, reconnects
     * with exponential backoff + random jitter.
     */
    fun observe(): Flow<WorldState> = callbackFlow {
        var delayMs = reconnectDelayMs
        var attemptCount = 0
        AppLogger.i("StateSSE", "GET /v1/stream starting, token=${if (token.isNotBlank()) "yes" else "no"}")

        while (isActive) {
            attemptCount++
            val request = Request.Builder()
                .url("$baseUrl/v1/stream")
                .header("Accept", "text/event-stream")
                .apply { if (token.isNotBlank()) header("X-Agent-Token", token) }
                .build()

            try {
                AppLogger.d("StateSSE", "SSE connect attempt #$attemptCount")
                withContext(Dispatchers.IO) {
                    okHttp.newCall(request).execute()
                }.use { response ->
                    if (!response.isSuccessful) {
                        AppLogger.w("StateSSE", "SSE HTTP ${response.code}: ${response.message}")
                        return@use // falls to catch-like handling below
                    }
                    AppLogger.i("StateSSE", "SSE connected, status=${response.code}")

                    val body = response.body ?: run {
                        AppLogger.w("StateSSE", "SSE response body is null")
                        return@use
                    }
                    val source = body.source()
                    var frameCount = 0
                    var heartbeatCount = 0

                    try {
                        while (isActive) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                // SSE comment — keepalive heartbeat
                                line.startsWith(":") -> {
                                    heartbeatCount++
                                    delayMs = reconnectDelayMs
                                }
                                // SSE data frame
                                line.startsWith("data: ") -> {
                                    val data = line.removePrefix("data: ").trim()
                                    if (data.isNotEmpty()) {
                                        try {
                                            val ws = json.decodeFromString(WorldState.serializer(), data)
                                            trySend(ws)
                                            frameCount++
                                            delayMs = reconnectDelayMs
                                        } catch (_: Exception) { /* skip malformed frames */ }
                                    }
                                }
                                // event:, id:, retry: — ignored
                                else -> { /* no-op */ }
                            }
                        }
                        AppLogger.i("StateSSE", "SSE stream ended, frames=$frameCount heartbeats=$heartbeatCount")
                    } catch (e: IOException) {
                        AppLogger.w("StateSSE", "SSE read error: ${e.message}")
                    }
                }
                // Connection ended cleanly — reset backoff
                delayMs = reconnectDelayMs
            } catch (e: IOException) {
                AppLogger.w("StateSSE", "SSE error (attempt #$attemptCount): ${e.javaClass.simpleName}: ${e.message}")
            } catch (e: Exception) {
                AppLogger.w("StateSSE", "SSE unexpected error (attempt #$attemptCount): ${e.javaClass.simpleName}: ${e.message}")
            }

            if (isActive) {
                val jitter = (delayMs * 0.25 * (Random.nextDouble() * 2 - 1)).toLong()
                val waitMs = (delayMs + jitter).coerceIn(reconnectDelayMs, maxReconnectDelayMs)
                AppLogger.d("StateSSE", "Reconnecting in ${waitMs}ms (base=${delayMs}ms)")
                delay(waitMs)
                delayMs = (delayMs * 2).coerceAtMost(maxReconnectDelayMs)
            }
        }
        awaitClose {
            AppLogger.i("StateSSE", "SSE observer closed")
        }
    }
}
