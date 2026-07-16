package com.pressureagent.mobile.data.mock

import com.pressureagent.mobile.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * OkHttp [Interceptor] that implements a complete mock of the agent-api.
 *
 * Follows the canonical 9-step story defined in [StoryScript] (v0.2).
 * Any POST to /v1/event advances one step. [EventType.SESSION_RESET] resets.
 */
class MockAgent : Interceptor {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val jsonMediaType = "application/json".toMediaType()

    @Volatile var step: Int = 0
        private set
    @Volatile private var revision: Int = 0
    @Volatile private var sessionId: String = "mock-session-${UUID.randomUUID().toString().take(8)}"
    @Volatile private var customTasks: MutableList<Task> = mutableListOf()

    val currentStep: Int get() = step

    fun reset() { step = 0; revision = 0; sessionId = "mock-session-${UUID.randomUUID().toString().take(8)}"; customTasks.clear() }
    fun jumpTo(targetStep: Int) { step = targetStep.coerceIn(0, StoryScript.STEP_COUNT - 1); revision++ }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        return when {
            req.method == "GET"  && req.url.encodedPath == "/health"          -> handleHealth()
            req.method == "GET"  && req.url.encodedPath == "/v1/state"        -> handleGetWorldState()
            req.method == "POST" && req.url.encodedPath == "/v1/event"         -> handlePostEvent(req)
            req.method == "GET"  && req.url.encodedPath == "/v1/stream"       -> handleStream()
            else -> chain.proceed(req)
        }
    }

    private fun handleHealth() = jsonResponse(200, """{"status":"ok"}""")

    private fun handleGetWorldState(): Response {
        val snapshot = currentSnapshot()
        return jsonResponse(200, json.encodeToString(WorldState.serializer(), snapshot))
    }

    private fun handlePostEvent(request: okhttp3.Request): Response {
        val bodyStr = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } ?: return jsonResponse(400, """{"error":"empty body"}""")
        val event: Event = try {
            json.decodeFromString(Event.serializer(), bodyStr)
        } catch (e: Exception) {
            return jsonResponse(400, """{"error":"invalid event: ${e.message}"}""")
        }
        when (event.type) {
            EventType.SESSION_RESET -> reset()
            EventType.TASK_CREATED -> {
                val title = event.payload["title"]?.jsonPrimitive?.contentOrNull
                if (title != null) {
                    handleTaskCreated(event.payload)
                } else {
                    step = (step + 1) % StoryScript.STEP_COUNT
                    revision++
                }
            }
            else -> {
                step = (step + 1) % StoryScript.STEP_COUNT
                revision++
            }
        }
        return jsonResponse(202, json.encodeToString(EventResponse.serializer(),
            EventResponse(event.eventId, accepted = true, revision = revision)))
    }

    private fun handleStream() =
        jsonResponse(501, """{"error":"SSE not available in mock mode"}""")

    private fun handleTaskCreated(payload: kotlinx.serialization.json.JsonObject) {
        val title = payload["title"]?.jsonPrimitive?.contentOrNull ?: return
        val location = payload["location"]?.jsonPrimitive?.contentOrNull
        val scheduledAt = payload["scheduledAt"]?.jsonPrimitive?.contentOrNull
        val typeStr = payload["type"]?.jsonPrimitive?.contentOrNull ?: "rigid"
        val priorityStr = payload["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
        val waitingParties = payload["waitingParties"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

        val task = Task(
            taskId = "task_${UUID.randomUUID().toString().take(8)}",
            title = title,
            location = location,
            scheduledAt = scheduledAt,
            taskType = if (typeStr == "rigid") TaskType.RIGID else TaskType.FLEXIBLE,
            priority = when (priorityStr) { "high" -> Priority.HIGH; "low" -> Priority.LOW; else -> Priority.MEDIUM },
            adjustable = typeStr != "rigid",
            status = TaskStatus.PENDING,
            waitingParty = waitingParties,
            capabilityTags = if (typeStr == "rigid") listOf("pickup", "rigid_deadline") else listOf("grocery"),
        )
        customTasks.add(0, task)
        revision++
    }

    private fun currentSnapshot(): WorldState {
        val now = ZonedDateTime.now()
        val base = StoryScript.forStep(step, sessionId, revision, now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        return if (customTasks.isEmpty()) base
        else base.copy(tasks = customTasks.toList() + base.tasks)
    }

    private fun jsonResponse(code: Int, body: String) = Response.Builder()
        .request(okhttp3.Request.Builder().url("http://localhost/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 200..299) "OK" else "Error")
        .body(body.toResponseBody(jsonMediaType))
        .build()

    companion object {
        const val MOCK_BASE_URL = "http://mock-agent.local/"
    }
}
