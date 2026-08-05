package com.pressureagent.mobile.data.voice

import android.util.Base64
import com.pressureagent.mobile.data.local.AppLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Xunfei (iFlytek) super-human-like TTS streaming client.
 *
 * Connects to 讯飞超拟人语音合成 v2 via WebSocket, sends text,
 * receives PCM 16kHz 16bit mono audio frames streamed as [Flow].
 *
 * Auth: HMAC-SHA256 signature per Xunfei WebSocket API spec.
 * No extra dependencies — uses OkHttp WebSocket + javax.crypto.
 */
class XunfeiTtsClient(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val voiceName: String = "x6_lingxiaoyue_pro",  // 超拟人发音人 聆小玥
    private val speed: Int = 50,    // 语速 0-100
    private val volume: Int = 50,   // 音量 0-100
    private val pitch: Int = 50,    // 音调 0-100
) {
    private companion object {
        const val HOST = "tts-api.xfyun.cn"
        const val PATH = "/v2/tts"
        const val URL_TEMPLATE = "wss://$HOST$PATH"
        const val TAG = "XunfeiTTS"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Synthesize [text] and stream PCM audio frames.
     * Each emission is a chunk of raw PCM 16-bit mono 16kHz data.
     * The flow completes when synthesis finishes or errors.
     */
    fun synthesize(text: String): Flow<SynthesisResult> = callbackFlow {
        val authUrl = buildAuthUrl()
        var webSocket: WebSocket? = null
        var completed = false

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                AppLogger.i(TAG, "WebSocket connected, sending params")
                try {
                    val params = buildParams(text)
                    ws.send(params)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to build/send params", e)
                    trySend(SynthesisResult.Error("参数构建失败: ${e.message}"))
                    ws.close(1000, null)
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = json.parseToJsonElement(text).jsonObject
                    val code = obj["code"]?.jsonPrimitive?.int ?: 0
                    if (code != 0) {
                        val message = obj["message"]?.jsonPrimitive?.content ?: "unknown"
                        AppLogger.e(TAG, "API error: code=$code message=$message")
                        if (!completed) {
                            trySend(SynthesisResult.Error("讯飞 TTS 错误 ($code): $message"))
                        }
                        return
                    }

                    val data = obj["data"]?.jsonObject ?: return
                    val status = data["status"]?.jsonPrimitive?.int ?: return
                    val audio = data["audio"]?.jsonPrimitive?.content ?: ""

                    if (audio.isNotEmpty()) {
                        val pcm = Base64.decode(audio, Base64.DEFAULT)
                        if (pcm.isNotEmpty()) {
                            trySend(SynthesisResult.AudioChunk(pcm))
                        }
                    }

                    // status 2 = all data sent, close connection
                    if (status >= 2) {
                        completed = true
                        ws.close(1000, "Done")
                        close()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to parse message", e)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                AppLogger.d(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                AppLogger.i(TAG, "WebSocket closed: $code $reason")
                if (!completed) {
                    trySend(SynthesisResult.Error("连接已关闭 (code=$code)"))
                }
                close()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e(TAG, "WebSocket failure", t)
                if (!completed) {
                    trySend(SynthesisResult.Error("连接失败: ${t.message}"))
                }
                close(t)
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                // Audio comes as base64-encoded JSON, not raw binary
            }
        }

        try {
            val request = Request.Builder()
                .url(authUrl)
                .build()
            webSocket = client.newWebSocket(request, listener)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create WebSocket", e)
            trySend(SynthesisResult.Error("WebSocket 创建失败: ${e.message}"))
            close(e)
        }

        awaitClose {
            AppLogger.d(TAG, "Flow closed, closing WebSocket")
            webSocket?.close(1000, "Client cancelled")
        }
    }

    private fun buildAuthUrl(): String {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date())

        val signatureOrigin = "host: $HOST\ndate: $date\nGET $PATH HTTP/1.1"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = Base64.encodeToString(mac.doFinal(signatureOrigin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

        val authorization = "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        // Xunfei requires: base64(authorization_string) → URL-encode
        val authBase64 = Base64.encodeToString(authorization.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val encodedDate = java.net.URLEncoder.encode(date, "UTF-8")
        val encodedAuth = java.net.URLEncoder.encode(authBase64, "UTF-8")

        return "${URL_TEMPLATE}?host=$HOST&date=$encodedDate&authorization=$encodedAuth"
    }

    private fun buildParams(text: String): String {
        val textBase64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val params = buildJsonObject {
            putJsonObject("common") {
                put("app_id", appId)
            }
            putJsonObject("business") {
                put("aue", "raw")
                put("auf", "audio/L16;rate=16000")
                put("vcn", voiceName)
                put("tte", "utf8")
                put("speed", speed)
                put("volume", volume)
                put("pitch", pitch)
            }
            putJsonObject("data") {
                put("status", 2) // 2 = last (and only) frame
                put("text", textBase64)
            }
        }

        return Json.encodeToString(JsonObject.serializer(), params)
    }
}

/**
 * Result from a TTS synthesis stream.
 */
sealed class SynthesisResult {
    /** A chunk of raw PCM 16-bit mono 16kHz audio data. */
    data class AudioChunk(val pcm: ByteArray) : SynthesisResult()

    /** Synthesis failed with [message]. */
    data class Error(val message: String) : SynthesisResult()
}
