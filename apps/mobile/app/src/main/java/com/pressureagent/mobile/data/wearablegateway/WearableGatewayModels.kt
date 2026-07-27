package com.pressureagent.mobile.data.wearablegateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val WEARABLE_GATEWAY_HOST = "127.0.0.1"
const val WEARABLE_GATEWAY_PORT = 8765
const val WEARABLE_GATEWAY_BASE_URL = "http://$WEARABLE_GATEWAY_HOST:$WEARABLE_GATEWAY_PORT"

@Serializable
data class WatchSetStateCommand(
    @SerialName("command_id") val commandId: String,
    val mode: String,
    val icon: String,
    val title: String,
    val text: String,
    val color: Int,
    val dimColor: Int,
    val haptic: String,
    @SerialName("duration_ms") val durationMs: Int = 3000,
    val source: String = "android-gateway",
)

@Serializable
data class WatchGatewayEnvelope(
    val method: String,
    val params: JsonObject,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class WatchOutboxResponse(
    val result: String = "ok",
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("set_state") val setState: WatchGatewayEnvelope? = null,
    @SerialName("sensor_request") val sensorRequest: WatchGatewayEnvelope? = null,
)

@Serializable
data class WatchInboxResponse(
    val result: String = "ok",
    val timestamp: Long = System.currentTimeMillis(),
)

data class WearableGatewaySnapshot(
    val running: Boolean = false,
    val baseUrl: String = WEARABLE_GATEWAY_BASE_URL,
    val lastOutboxCommandId: String = "",
    val lastSideContactAt: Long = 0L,
    val lastHello: JsonObject? = null,
    val lastAck: JsonObject? = null,
    val lastSensor: JsonObject? = null,
    val lastPong: JsonObject? = null,
    val lastSensorRequestId: String = "",
    val lastSubmittedSensorEventId: String = "",
    val lastError: String = "",
)
