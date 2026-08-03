package com.pressureagent.mobile.data.wearablegateway

import com.pressureagent.mobile.data.repository.ConnectionStatus
import com.pressureagent.mobile.data.repository.WorldStateRepository
import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventResponse
import com.pressureagent.mobile.domain.model.EventSource
import com.pressureagent.mobile.domain.model.EventType
import com.pressureagent.mobile.domain.model.HapticPattern
import com.pressureagent.mobile.domain.model.PressureLevel
import com.pressureagent.mobile.domain.model.Risk
import com.pressureagent.mobile.domain.model.Stage
import com.pressureagent.mobile.domain.model.Wearable
import com.pressureagent.mobile.domain.model.WearableColor
import com.pressureagent.mobile.domain.model.WearableMode
import com.pressureagent.mobile.domain.model.WorldState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

class WearableGatewayTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun exposesHealthOutboxAndRecordsInboxMessages() {
        val repository = FakeWorldStateRepository(
            WorldState(
                sessionId = "session-gateway",
                revision = 3,
                stage = Stage.PRE_DEPARTURE_WARNING,
                risk = Risk(pressureLevel = PressureLevel.L1),
                wearable = Wearable(
                    connected = true,
                    mode = WearableMode.WARNING,
                    text = "Prepare takeover",
                    color = WearableColor.YELLOW,
                    haptic = HapticPattern.DOUBLE_SHORT,
                    commandId = "cmd-warning-3",
                ),
            ),
        )
        val gateway = WearableGateway(repository, json)

        try {
            gateway.start()

            val health = eventuallyJson { httpGet("/health") }
            assertEquals("ok", health["result"]?.jsonPrimitive?.content)

            val outbox = eventuallyJsonObject {
                json.decodeFromString<JsonElement>(
                    httpGet("/v1/watch/outbox?last_command_id=&last_sensor_request_id="),
                ).jsonObject.also { response ->
                    check(response["set_state"] is JsonObject) { "outbox set_state is not ready" }
                }
            }
            val setState = outbox["set_state"]!!.jsonObject
            val params = setState["params"]!!.jsonObject
            assertEquals("watch.setState", setState["method"]?.jsonPrimitive?.content)
            assertEquals("cmd-warning-3", params["command_id"]?.jsonPrimitive?.content)
            assertEquals("warning", params["mode"]?.jsonPrimitive?.content)
            assertEquals("double_short", params["haptic"]?.jsonPrimitive?.content)
            eventuallyTrue { gateway.state.value.lastAgentCommandId == "cmd-warning-3" }
            eventuallyTrue { gateway.state.value.lastOutboxSource == "agent-world-state" }

            httpPost(
                "/v1/watch/inbox",
                """{"method":"watch.ack","params":{"command_id":"cmd-warning-3","result":"ok"}}""",
            )
            httpPost(
                "/v1/watch/inbox",
                """{"method":"watch.pong","params":{"ping_id":"ping-1","timestamp":1000}}""",
            )
            httpPost(
                "/v1/watch/inbox",
                """{"method":"watch.sensor","params":{"heart_rate":88,"confidence":0.91,"timestamp":2000}}""",
            )

            eventuallyTrue { gateway.state.value.lastAck?.get("result")?.jsonPrimitive?.content == "ok" }
            eventuallyTrue { gateway.state.value.lastPong?.get("ping_id")?.jsonPrimitive?.content == "ping-1" }
            eventuallyTrue { repository.submittedEvents.isNotEmpty() }

            val event = repository.submittedEvents.single()
            assertEquals(EventType.WEARABLE_SIGNAL, event.type)
            assertEquals(EventSource.WEARABLE, event.source)
            assertEquals(88, event.payload["heart_rate"]?.jsonPrimitive?.int)
            assertEquals(0.91, event.payload["confidence"]?.jsonPrimitive?.double)
        } finally {
            gateway.stop()
        }
    }

    @Test
    fun queuesCompletedBeforeRecoveryIdle() {
        val repository = FakeWorldStateRepository(
            WorldState(
                sessionId = "demo",
                revision = 1,
                stage = Stage.ACTION_COMPLETED,
            ),
        )
        val gateway = WearableGateway(repository, json)

        try {
            gateway.start()
            eventuallyTrue { gateway.state.value.lastAgentCommandId == "world-demo-1" }

            repository.update(
                WorldState(
                    sessionId = "demo",
                    revision = 2,
                    stage = Stage.PARKED_REVIEW,
                ),
            )
            eventuallyTrue { gateway.state.value.lastAgentCommandId == "world-demo-2" }

            val firstOutbox = eventuallyJsonObject {
                json.decodeFromString<JsonElement>(
                    httpGet("/v1/watch/outbox?last_command_id=&last_sensor_request_id="),
                ).jsonObject.also { response ->
                    check(response["set_state"] is JsonObject) { "first set_state is not ready" }
                }
            }
            val firstParams = firstOutbox["set_state"]!!.jsonObject["params"]!!.jsonObject
            assertEquals("world-demo-1", firstParams["command_id"]?.jsonPrimitive?.content)
            assertEquals("completed", firstParams["mode"]?.jsonPrimitive?.content)
            assertEquals("soft_short", firstParams["haptic"]?.jsonPrimitive?.content)

            httpPost(
                "/v1/watch/inbox",
                """{"method":"watch.ack","params":{"command_id":"world-demo-1","result":"ok"}}""",
            )

            val secondOutbox = eventuallyJsonObject {
                json.decodeFromString<JsonElement>(
                    httpGet("/v1/watch/outbox?last_command_id=world-demo-1&last_sensor_request_id="),
                ).jsonObject.also { response ->
                    check(response["set_state"] is JsonObject) { "second set_state is not ready" }
                }
            }
            val secondParams = secondOutbox["set_state"]!!.jsonObject["params"]!!.jsonObject
            assertEquals("world-demo-2", secondParams["command_id"]?.jsonPrimitive?.content)
            assertEquals("idle", secondParams["mode"]?.jsonPrimitive?.content)
            assertEquals("none", secondParams["haptic"]?.jsonPrimitive?.content)
        } finally {
            gateway.stop()
        }
    }

    private fun httpGet(path: String): String =
        openConnection(path).run {
            requestMethod = "GET"
            readText()
        }

    private fun httpPost(path: String, body: String): String =
        openConnection(path).run {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body) }
            readText()
        }

    private fun openConnection(path: String): HttpURLConnection =
        (URL("$WEARABLE_GATEWAY_BASE_URL$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 500
            readTimeout = 500
        }

    private fun HttpURLConnection.readText(): String =
        try {
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            disconnect()
        }

    private fun eventuallyJson(block: () -> String) =
        json.decodeFromString<JsonElement>(eventually(block)).jsonObject

    private fun eventuallyJsonObject(block: () -> JsonObject) =
        eventuallyValue(block)

    private fun eventually(block: () -> String): String {
        return eventuallyValue(block)
    }

    private fun <T> eventuallyValue(block: () -> T): T {
        var lastError: Throwable? = null
        repeat(40) {
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                Thread.sleep(50)
            }
        }
        throw AssertionError("Timed out waiting for gateway response", lastError)
    }

    private fun eventuallyTrue(predicate: () -> Boolean) {
        repeat(40) {
            if (predicate()) {
                return
            }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for gateway state")
    }
}

private class FakeWorldStateRepository(initialState: WorldState) : WorldStateRepository {
    private val state = MutableStateFlow(initialState)
    val submittedEvents = mutableListOf<Event>()

    override val worldState: Flow<WorldState> = state
    override val connectionStatus: StateFlow<ConnectionStatus> = MutableStateFlow(ConnectionStatus.CONNECTED)

    override suspend fun refresh(): WorldState = state.value

    override suspend fun submitEvent(event: Event): EventResponse {
        submittedEvents += event
        return EventResponse(eventId = event.eventId, accepted = true, revision = state.value.revision)
    }

    override suspend fun resetSession(): WorldState = state.value

    fun update(nextState: WorldState) {
        state.value = nextState
    }
}
