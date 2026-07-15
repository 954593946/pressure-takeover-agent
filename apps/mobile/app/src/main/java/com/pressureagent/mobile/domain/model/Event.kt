package com.pressureagent.mobile.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class Event(
    @SerialName("schemaVersion")
    val schemaVersion: String = "0.1.0",
    val eventId: String,
    val type: EventType,
    val source: EventSource,
    val occurredAt: String,
    val correlationId: String? = null,
    val deviceId: String? = null,
    val payload: JsonObject,
)

@Serializable
enum class EventType {
    @SerialName("task.input.received") TASK_INPUT_RECEIVED,
    @SerialName("meeting.delay.reported") MEETING_DELAY_REPORTED,
    @SerialName("vehicle.mode.changed") VEHICLE_MODE_CHANGED,
    @SerialName("traffic.updated") TRAFFIC_UPDATED,
    @SerialName("pressure.signal.reported") PRESSURE_SIGNAL_REPORTED,
    @SerialName("assistance.requested") ASSISTANCE_REQUESTED,
    @SerialName("confirmation.submitted") CONFIRMATION_SUBMITTED,
    @SerialName("wearable.status.reported") WEARABLE_STATUS_REPORTED,
    @SerialName("demo.reset.requested") DEMO_RESET_REQUESTED,
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
    val eventId: String,
    val accepted: Boolean,
    val revision: Int,
)
