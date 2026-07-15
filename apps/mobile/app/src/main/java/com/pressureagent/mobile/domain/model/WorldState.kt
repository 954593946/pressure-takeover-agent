package com.pressureagent.mobile.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ─── Stage & agent mode ──────────────────────────────────────────────────────

@Serializable
enum class Stage {
    @SerialName("idle") IDLE,
    @SerialName("task_created") TASK_CREATED,
    @SerialName("meeting_delay") MEETING_DELAY,
    @SerialName("departure_warning") DEPARTURE_WARNING,
    @SerialName("vehicle_mode") VEHICLE_MODE,
    @SerialName("traffic_delay") TRAFFIC_DELAY,
    @SerialName("pressure_takeover") PRESSURE_TAKEOVER,
    @SerialName("waiting_confirmation") WAITING_CONFIRMATION,
    @SerialName("action_completed") ACTION_COMPLETED,
    @SerialName("role_transition") ROLE_TRANSITION,
}

@Serializable
enum class AgentMode {
    @SerialName("quiet") QUIET,
    @SerialName("observing") OBSERVING,
    @SerialName("taking_over") TAKING_OVER,
    @SerialName("awaiting_confirmation") AWAITING_CONFIRMATION,
    @SerialName("resolved") RESOLVED,
}

// ─── Task ────────────────────────────────────────────────────────────────────

@Serializable
enum class TaskType {
    @SerialName("rigid") RIGID,
    @SerialName("flexible") FLEXIBLE,
}

@Serializable
enum class Priority {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
}

@Serializable
enum class TaskStatus {
    @SerialName("pending") PENDING,
    @SerialName("rescheduled") RESCHEDULED,
    @SerialName("completed") COMPLETED,
}

@Serializable
data class Task(
    val id: String,
    val title: String,
    val scheduledAt: String? = null,
    val location: String? = null,
    val type: TaskType,
    val priority: Priority,
    val adjustable: Boolean,
    val status: TaskStatus,
    val waitingParties: List<String>? = null,
)

// ─── Vehicle ─────────────────────────────────────────────────────────────────

@Serializable
enum class VehicleMode {
    @SerialName("off") OFF,
    @SerialName("parked") PARKED,
    @SerialName("driving") DRIVING,
}

@Serializable
data class Vehicle(
    val mode: VehicleMode,
    val destination: String? = null,
    val eta: String? = null,
    val delayMinutes: Int,
)

// ─── Risk ────────────────────────────────────────────────────────────────────

@Serializable
enum class RiskLevel {
    @SerialName("none") NONE,
    @SerialName("watch") WATCH,
    @SerialName("high") HIGH,
}

@Serializable
data class Risk(
    val level: RiskLevel,
    val realityRiskConfirmed: Boolean,
    val auxiliarySignalPresent: Boolean,
    val reasons: List<String>,
)

// ─── Wearable ────────────────────────────────────────────────────────────────

@Serializable
enum class WearableState {
    @SerialName("idle") IDLE,
    @SerialName("ready") READY,
    @SerialName("warning") WARNING,
    @SerialName("driving") DRIVING,
    @SerialName("taking_over") TAKING_OVER,
    @SerialName("resolved") RESOLVED,
    @SerialName("offline") OFFLINE,
}

@Serializable
enum class Vibration {
    @SerialName("none") NONE,
    @SerialName("short") SHORT,
    @SerialName("rhythmic") RHYTHMIC,
}

@Serializable
enum class HeartRateSource {
    @SerialName("device") DEVICE,
    @SerialName("simulated") SIMULATED,
}

@Serializable
data class Wearable(
    val connected: Boolean,
    val state: WearableState,
    val text: String? = null,
    val vibration: Vibration,
    val heartRate: Int? = null,
    val heartRateSource: HeartRateSource? = null,
)

// ─── Action ──────────────────────────────────────────────────────────────────

@Serializable
enum class ActionType {
    @SerialName("reschedule") RESCHEDULE,
    @SerialName("draft_message") DRAFT_MESSAGE,
    @SerialName("send_message") SEND_MESSAGE,
}

@Serializable
enum class ActionRiskLevel {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
}

@Serializable
enum class ActionStatus {
    @SerialName("planned") PLANNED,
    @SerialName("ready") READY,
    @SerialName("awaiting_confirmation") AWAITING_CONFIRMATION,
    @SerialName("completed") COMPLETED,
    @SerialName("blocked") BLOCKED,
}

@Serializable
data class Action(
    val id: String,
    val type: ActionType,
    val target: String,
    val summary: String? = null,
    val riskLevel: ActionRiskLevel,
    val requiresConfirmation: Boolean,
    val status: ActionStatus,
)

// ─── Message ─────────────────────────────────────────────────────────────────

@Serializable
enum class Audience {
    @SerialName("teacher") TEACHER,
    @SerialName("family") FAMILY,
}

@Serializable
enum class MessageStatus {
    @SerialName("draft") DRAFT,
    @SerialName("awaiting_confirmation") AWAITING_CONFIRMATION,
    @SerialName("simulated_sent") SIMULATED_SENT,
}

@Serializable
data class Message(
    val id: String,
    val audience: Audience,
    val displayName: String? = null,
    val body: String,
    val status: MessageStatus,
)

// ─── Confirmation ────────────────────────────────────────────────────────────

@Serializable
enum class ConfirmationStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("expired") EXPIRED,
}

@Serializable
data class Confirmation(
    val id: String,
    val prompt: String,
    val status: ConfirmationStatus,
    val actionIds: List<String>,
)

// ─── Root World State ────────────────────────────────────────────────────────

@Serializable
data class WorldState(
    @SerialName("schemaVersion")
    val schemaVersion: String = "0.1.0",
    val revision: Int,
    val updatedAt: String,
    val stage: Stage,
    val agentMode: AgentMode,
    val tasks: List<Task>,
    val vehicle: Vehicle,
    val risk: Risk,
    val wearable: Wearable,
    val actions: List<Action>,
    val messages: List<Message>,
    val conclusion: String? = null,
    val confirmation: Confirmation? = null,
)
