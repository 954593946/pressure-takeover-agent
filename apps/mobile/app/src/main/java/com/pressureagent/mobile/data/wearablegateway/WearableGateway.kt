package com.pressureagent.mobile.data.wearablegateway

import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventSource
import com.pressureagent.mobile.domain.model.EventType
import com.pressureagent.mobile.domain.model.HapticPattern
import com.pressureagent.mobile.domain.model.WearableColor
import com.pressureagent.mobile.domain.model.WearableMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableGateway @Inject constructor(
    private val repository: WorldStateRepository,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WearableGatewaySnapshot())
    val state: StateFlow<WearableGatewaySnapshot> = _state.asStateFlow()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var latestSetState: WatchSetStateCommand? = null
    @Volatile private var latestSessionId: String = ""
    @Volatile private var started: Boolean = false
    private val submittedSensorEvents = ArrayDeque<String>()

    fun start() {
        if (started) {
            return
        }

        started = true
        observeWorldState()
        scope.launch {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName(WEARABLE_GATEWAY_HOST), WEARABLE_GATEWAY_PORT))
                }
                serverSocket = server
                setState { it.copy(running = true, lastError = "") }
                AppLogger.i("WearableGateway", "网关已启动: $WEARABLE_GATEWAY_BASE_URL")

                while (isActive) {
                    val socket = server.accept()
                    scope.launch {
                        handleClient(socket)
                    }
                }
            } catch (error: Exception) {
                val shouldLogError = started
                try {
                    serverSocket?.close()
                } catch (_: Exception) {
                }
                serverSocket = null
                started = false
                setState { it.copy(running = false, lastError = error.message ?: "server stopped") }
                if (shouldLogError) {
                    AppLogger.e("WearableGateway", "网关异常", error)
                }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        started = false
        setState { it.copy(running = false) }
    }

    fun requestHealthSnapshot(reason: String = "android-ui") {
        val requestId = "android-sensor-${System.currentTimeMillis()}"
        val params = buildJsonObject {
            put("request_id", JsonPrimitive(requestId))
            put("reason", JsonPrimitive(reason))
            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
        }
        pendingSensorRequest = WatchGatewayEnvelope(
            method = "watch.sensorRequest",
            params = params,
        )
        setState { it.copy(lastSensorRequestId = requestId) }
        AppLogger.i("WearableGateway", "请求手表健康快照: $requestId")
    }

    fun sendDebugState(mode: WearableMode) {
        val command = WearableCommandMapper.toWatchCommand(
            commandId = "android-debug-${mode.name.lowercase()}-${System.currentTimeMillis()}",
            mode = mode,
            color = debugColorFor(mode),
            haptic = debugHapticFor(mode),
            source = "android-debug",
        )
        queueDebugCommand(command)
    }

    fun sendDebugHaptic(haptic: HapticPattern, mode: WearableMode = latestModeForDebug()) {
        val command = WearableCommandMapper.toWatchCommand(
            commandId = "android-haptic-${haptic.name.lowercase()}-${System.currentTimeMillis()}",
            mode = mode,
            text = "触觉测试：${debugHapticLabel(haptic)}",
            color = debugColorFor(mode),
            haptic = haptic,
            source = "android-debug",
        )
        queueDebugCommand(command)
    }

    @Volatile private var pendingSensorRequest: WatchGatewayEnvelope? = null

    private fun queueDebugCommand(command: WatchSetStateCommand) {
        latestSetState = command
        setState {
            it.copy(
                lastOutboxCommandId = command.commandId,
                lastOutboxSource = command.source,
                lastError = "",
            )
        }
        AppLogger.i("WearableGateway", "调试命令已入队: ${command.commandId}")
    }

    private fun observeWorldState() {
        scope.launch {
            repository.worldState.collect { worldState ->
                latestSessionId = worldState.sessionId
                val command = WearableCommandMapper.toWatchCommand(worldState)
                latestSetState = command
                if (command != null) {
                    setState {
                        it.copy(
                            lastOutboxCommandId = command.commandId,
                            lastOutboxSource = command.source,
                            lastAgentCommandId = command.commandId,
                            lastAgentCommandMode = command.mode,
                            lastAgentCommandText = command.text,
                            lastAgentCommandHaptic = command.haptic,
                        )
                    }
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use {
            val request = readHttpRequest(socket) ?: return
            val response = when {
                request.method == "GET" && request.path == "/health" -> {
                    okJson(
                        buildJsonObject {
                            put("result", JsonPrimitive("ok"))
                            put("running", JsonPrimitive(true))
                            put("base_url", JsonPrimitive(WEARABLE_GATEWAY_BASE_URL))
                            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
                        },
                    )
                }
                request.method == "GET" && request.path == "/v1/watch/outbox" -> {
                    handleOutbox(request.query)
                }
                request.method == "POST" && request.path == "/v1/watch/inbox" -> {
                    handleInbox(request.body)
                }
                else -> {
                    httpResponse(404, """{"result":"not_found"}""")
                }
            }
            socket.getOutputStream().write(response)
        }
    }

    private fun handleOutbox(query: Map<String, String>): ByteArray {
        val lastCommandId = query["last_command_id"].orEmpty()
        val lastSensorRequestId = query["last_sensor_request_id"].orEmpty()
        val command = latestSetState
        val setStateEnvelope = if (command != null && command.commandId != lastCommandId) {
            WatchGatewayEnvelope(
                method = "watch.setState",
                params = json.encodeToJsonElement(command).jsonObject,
            )
        } else {
            null
        }
        val sensorEnvelope = pendingSensorRequest?.takeIf { envelope ->
            envelope.params.stringValue("request_id") != lastSensorRequestId
        }

        val response = WatchOutboxResponse(
            setState = setStateEnvelope,
            sensorRequest = sensorEnvelope,
        )
        return okJson(response)
    }

    private fun handleInbox(body: String): ByteArray {
        return try {
            val payload = json.decodeFromString<JsonElement>(body).jsonObject
            val method = payload.stringValue("method").ifBlank {
                payload.stringValue("type")
            }
            val params = payload["params"]?.safeJsonObject() ?: payload

            setState { snapshot ->
                when (method) {
                    "watch.hello" -> snapshot.copy(lastSideContactAt = System.currentTimeMillis(), lastHello = params, lastError = "")
                    "watch.ack" -> snapshot.copy(lastSideContactAt = System.currentTimeMillis(), lastAck = params, lastError = "")
                    "watch.sensor" -> snapshot.copy(lastSideContactAt = System.currentTimeMillis(), lastSensor = params, lastError = "")
                    "watch.pong" -> snapshot.copy(lastSideContactAt = System.currentTimeMillis(), lastPong = params, lastError = "")
                    else -> snapshot.copy(lastSideContactAt = System.currentTimeMillis(), lastError = "")
                }
            }

            if (method == "watch.sensor") {
                submitSensorSignal(params)
            }
            okJson(WatchInboxResponse())
        } catch (error: Exception) {
            setState { it.copy(lastError = error.message ?: "inbox parse failed") }
            AppLogger.e("WearableGateway", "inbox 解析失败", error)
            httpResponse(400, """{"result":"error"}""")
        }
    }

    private fun submitSensorSignal(params: JsonObject) {
        val heartRate = params.intValue("heart_rate") ?: return
        val sessionId = latestSessionId.ifBlank { return }
        val sensorTimestamp = params.longValue("timestamp") ?: System.currentTimeMillis()
        val eventId = "evt_watch_sensor_${sensorTimestamp}_$heartRate"

        if (submittedSensorEvents.contains(eventId)) {
            return
        }
        submittedSensorEvents.addLast(eventId)
        while (submittedSensorEvents.size > 50) {
            submittedSensorEvents.removeFirst()
        }

        val confidence = params.doubleValue("confidence")
            ?: if (params.stringValue("result") == "ok") 0.7 else 0.0
        val event = Event(
            eventId = eventId,
            sessionId = sessionId,
            type = EventType.WEARABLE_SIGNAL,
            source = EventSource.WEARABLE,
            timestamp = nowIso(),
            deviceId = "active2-round",
            payload = buildJsonObject {
                put("heart_rate", JsonPrimitive(heartRate))
                put("confidence", JsonPrimitive(confidence.coerceIn(0.0, 1.0)))
            },
        )

        scope.launch {
            try {
                repository.submitEvent(event)
                setState { it.copy(lastSubmittedSensorEventId = eventId, lastError = "") }
                AppLogger.i("WearableGateway", "已上报 wearable.signal: $eventId")
            } catch (error: Exception) {
                setState { it.copy(lastError = error.message ?: "sensor submit failed") }
                AppLogger.e("WearableGateway", "wearable.signal 上报失败", error)
            }
        }
    }

    private fun readHttpRequest(socket: Socket): HttpRequest? {
        val input = socket.getInputStream()
        val headerBytes = ByteArrayOutputStream()
        var match = 0
        while (true) {
            val next = input.read()
            if (next < 0) {
                return null
            }
            headerBytes.write(next)
            match = when {
                match == 0 && next == '\r'.code -> 1
                match == 1 && next == '\n'.code -> 2
                match == 2 && next == '\r'.code -> 3
                match == 3 && next == '\n'.code -> 4
                next == '\r'.code -> 1
                else -> 0
            }
            if (match == 4) {
                break
            }
        }

        val headerText = headerBytes.toString(Charsets.UTF_8.name())
        val lines = headerText.split("\r\n").filter { it.isNotBlank() }
        val requestLine = lines.firstOrNull()?.split(" ") ?: return null
        if (requestLine.size < 2) {
            return null
        }
        val method = requestLine[0].uppercase()
        val target = requestLine[1]
        val path = target.substringBefore("?")
        val query = parseQuery(target.substringAfter("?", ""))
        val contentLength = lines
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        val bodyBytes = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(bodyBytes, read, contentLength - read)
            if (count < 0) break
            read += count
        }

        return HttpRequest(
            method = method,
            path = path,
            query = query,
            body = bodyBytes.decodeToString(),
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) {
            return emptyMap()
        }
        return query.split("&").mapNotNull { part ->
            val key = part.substringBefore("=", "")
            if (key.isBlank()) return@mapNotNull null
            key to part.substringAfter("=", "")
        }.toMap()
    }

    private fun okJson(payload: JsonElement): ByteArray = httpResponse(200, payload.toString())

    private inline fun <reified T> okJson(payload: T): ByteArray = httpResponse(200, json.encodeToString(payload))

    private fun httpResponse(status: Int, body: String): ByteArray {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "OK"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        return buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8) + bytes
    }

    private fun setState(update: (WearableGatewaySnapshot) -> WearableGatewaySnapshot) {
        _state.value = update(_state.value)
    }

    private fun latestModeForDebug(): WearableMode = when (latestSetState?.mode) {
        "warning" -> WearableMode.WARNING
        "handover" -> WearableMode.HANDOVER
        "processing" -> WearableMode.PROCESSING
        "completed" -> WearableMode.COMPLETED
        "error" -> WearableMode.ERROR
        else -> WearableMode.IDLE
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val body: String,
    )
}

private fun debugColorFor(mode: WearableMode): WearableColor = when (mode) {
    WearableMode.WARNING -> WearableColor.YELLOW
    WearableMode.COMPLETED -> WearableColor.GREEN
    WearableMode.ERROR -> WearableColor.RED
    WearableMode.IDLE,
    WearableMode.HANDOVER,
    WearableMode.PROCESSING -> WearableColor.BLUE
}

private fun debugHapticFor(mode: WearableMode): HapticPattern = when (mode) {
    WearableMode.IDLE -> HapticPattern.NONE
    WearableMode.WARNING -> HapticPattern.DOUBLE_SHORT
    WearableMode.HANDOVER -> HapticPattern.SINGLE_PULSE
    WearableMode.PROCESSING -> HapticPattern.THREE_BEAT
    WearableMode.COMPLETED -> HapticPattern.SOFT_SHORT
    WearableMode.ERROR -> HapticPattern.ERROR_ONCE
}

private fun debugHapticLabel(haptic: HapticPattern): String = when (haptic) {
    HapticPattern.NONE -> "无"
    HapticPattern.DOUBLE_SHORT -> "双短震"
    HapticPattern.SINGLE_PULSE -> "单脉冲"
    HapticPattern.THREE_BEAT -> "三拍"
    HapticPattern.SOFT_SHORT -> "柔和短震"
    HapticPattern.ERROR_ONCE -> "错误震"
}

private fun JsonElement.safeJsonObject(): JsonObject? = if (this is JsonObject) this else null

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.intValue(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.longValue(key: String): Long? =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

private fun JsonObject.doubleValue(key: String): Double? {
    val primitive = this[key]?.jsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
}

private fun nowIso(): String =
    OffsetDateTime.now(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
