package com.pressureagent.mobile.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Vehicle Info ──────────────────────────────────────────────────────────

@Serializable
data class VehicleInfo(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("model_name") val modelName: String = "AURI ONE",
    @SerialName("license_plate") val licensePlate: String? = null,
    val connected: Boolean = false,
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
)

// ─── Vehicle Status ────────────────────────────────────────────────────────

@Serializable
data class VehicleStatus(
    @SerialName("battery_percent") val batteryPercent: Int = 72,
    @SerialName("range_km") val rangeKm: Int = 420,
    @SerialName("total_odometer_km") val totalOdometerKm: Int = 0,
    @SerialName("is_locked") val isLocked: Boolean = true,
    @SerialName("cabin_temp_celsius") val cabinTempCelsius: Double = 24.0,
    val location: String? = null,
    @SerialName("security_status") val securityStatus: String = "normal", // "normal" | "alert" | "alarm"
)

// ─── Trip Summary ──────────────────────────────────────────────────────────

@Serializable
data class TripSummary(
    @SerialName("trip_id") val tripId: String,
    val date: String,                   // ISO date "2026-07-15"
    val origin: String,
    val destination: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("had_takeover") val hadTakeover: Boolean = false,
)

// ─── Vehicle API Response ──────────────────────────────────────────────────

@Serializable
data class VehicleStateResponse(
    @SerialName("vehicle_info") val vehicleInfo: VehicleInfo,
    @SerialName("vehicle_status") val vehicleStatus: VehicleStatus,
    @SerialName("recent_trips") val recentTrips: List<TripSummary> = emptyList(),
)
