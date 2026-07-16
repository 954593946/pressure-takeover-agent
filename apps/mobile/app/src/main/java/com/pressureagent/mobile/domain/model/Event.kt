package com.pressureagent.mobile.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class Event(
    @SerialName("schema_version")
    val schemaVersion: String = "0.2.0",
    @SerialName("event_id") val eventId: String,
    @SerialName("session_id") val sessionId: String = "",
    val type: EventType,
    val source: EventSource,
    val timestamp: String,
    @SerialName("correlation_id") val correlationId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    val payload: JsonObject,
)

@Serializable
enum class EventType {
    @SerialName("task.created") TASK_CREATED,
    @SerialName("meeting.overrun") MEETING_OVERRUN,
    @SerialName("scene.approaching") SCENE_APPROACHING,
    @SerialName("scene.vehicle_entered") SCENE_VEHICLE_ENTERED,
    @SerialName("scene.parked") SCENE_PARKED,
    @SerialName("traffic.updated") TRAFFIC_UPDATED,
    @SerialName("wearable.signal") WEARABLE_SIGNAL,
    @SerialName("driving.signal") DRIVING_SIGNAL,
    @SerialName("user.utterance") USER_UTTERANCE,
    @SerialName("service.mock.config") SERVICE_MOCK_CONFIG,
    @SerialName("confirmation.confirmed") CONFIRMATION_CONFIRMED,
    @SerialName("cooldown.elapsed") COOLDOWN_ELAPSED,
    @SerialName("session.reset") SESSION_RESET,
}

@Serializable
enum class EventSource {
    @SerialName("mobile") MOBILE,
    @SerialName("vehicle_hmi") VEHICLE_HMI,
    @SerialName("wearable") WEARABLE,
    @SerialName("demo_console") DEMO_CONSOLE,
    @SerialName("agent_api") AGENT_API,
}

@Serializable
data class EventResponse(
    @SerialName("event_id") val eventId: String,
    val accepted: Boolean,
    val revision: Int,
)
