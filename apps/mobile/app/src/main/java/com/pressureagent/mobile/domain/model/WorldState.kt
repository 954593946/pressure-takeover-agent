package com.pressureagent.mobile.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ─── Stage ────────────────────────────────────────────────────────────────────

@Serializable
enum class Stage {
    @SerialName("off_vehicle_idle") OFF_VEHICLE_IDLE,
    @SerialName("pre_departure_warning") PRE_DEPARTURE_WARNING,
    @SerialName("handover_to_vehicle") HANDOVER_TO_VEHICLE,
    @SerialName("vehicle_observation") VEHICLE_OBSERVATION,
    @SerialName("takeover_L2") TAKEOVER_L2,
    @SerialName("takeover_L3") TAKEOVER_L3,
    @SerialName("planning") PLANNING,
    @SerialName("service_prepared") SERVICE_PREPARED,
    @SerialName("waiting_confirmation") WAITING_CONFIRMATION,
    @SerialName("executing") EXECUTING,
    @SerialName("service_executed") SERVICE_EXECUTED,
    @SerialName("action_completed") ACTION_COMPLETED,
    @SerialName("cooldown") COOLDOWN,
    @SerialName("parked_review") PARKED_REVIEW,
    @SerialName("error") ERROR,
}

// ─── Scene ────────────────────────────────────────────────────────────────────

@Serializable
enum class Scene {
    @SerialName("off_vehicle") OFF_VEHICLE,
    @SerialName("approaching_vehicle") APPROACHING_VEHICLE,
    @SerialName("driving") DRIVING,
    @SerialName("high_load_driving") HIGH_LOAD_DRIVING,
    @SerialName("parked") PARKED,
}

// ─── Primary Surface ──────────────────────────────────────────────────────────

@Serializable
enum class PrimarySurface {
    @SerialName("mobile") MOBILE,
    @SerialName("vehicle_hmi") VEHICLE_HMI,
    @SerialName("none") NONE,
}

// ─── Risk (v0.2: L0-L3 pressure levels) ──────────────────────────────────────

@Serializable
enum class PressureLevel {
    @SerialName("L0") L0,
    @SerialName("L1") L1,
    @SerialName("L2") L2,
    @SerialName("L3") L3,
    @SerialName("Recovery") RECOVERY,
}

@Serializable
data class Risk(
    @SerialName("pressure_level") val pressureLevel: PressureLevel,
    @SerialName("late_minutes") val lateMinutes: Int = 0,
    @SerialName("reason_codes") val reasonCodes: List<String> = emptyList(),
    @SerialName("auxiliary_signals") val auxiliarySignals: List<String> = emptyList(),
)

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
    @SerialName("task_id") val taskId: String,
    val title: String,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val location: String? = null,
    @SerialName("task_type") val taskType: TaskType,
    val priority: Priority,
    val adjustable: Boolean,
    val status: TaskStatus,
    @SerialName("waiting_party") val waitingParty: List<String> = emptyList(),
    @SerialName("capability_tags") val capabilityTags: List<String> = emptyList(),
)

// ─── Action ──────────────────────────────────────────────────────────────────

@Serializable
enum class ActionType {
    @SerialName("message") MESSAGE,
    @SerialName("reschedule") RESCHEDULE,
    @SerialName("service_order") SERVICE_ORDER,
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
    @SerialName("failed") FAILED,
}

@Serializable
data class Action(
    @SerialName("action_id") val actionId: String,
    val type: ActionType,
    val target: String,
    val status: ActionStatus,
    val risk: ActionRiskLevel,
    @SerialName("requires_confirmation") val requiresConfirmation: Boolean,
    val summary: String,
    @SerialName("details_ref") val detailsRef: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
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
    @SerialName("confirmation_id") val confirmationId: String,
    @SerialName("action_ids") val actionIds: List<String>,
    @SerialName("expires_at") val expiresAt: String,
    val status: ConfirmationStatus,
    @SerialName("confirmed_by") val confirmedBy: String? = null,
    @SerialName("owner_surface") val ownerSurface: String, // "mobile" | "vehicle_hmi"
)

// ─── Profile ─────────────────────────────────────────────────────────────────

@Serializable
enum class ProfileType {
    @SerialName("efficiency") EFFICIENCY,
    @SerialName("quality") QUALITY,
}

@Serializable
enum class VoiceThreshold {
    @SerialName("L1") L1,
    @SerialName("L2") L2,
    @SerialName("L3") L3,
}

@Serializable
enum class HapticMode {
    @SerialName("clear") CLEAR,
    @SerialName("gentle") GENTLE,
}

@Serializable
enum class DeliveryPriority {
    @SerialName("fastest") FASTEST,
    @SerialName("quality_first") QUALITY_FIRST,
}

@Serializable
enum class SubstitutionPolicy {
    @SerialName("same_spec_within_budget") SAME_SPEC_WITHIN_BUDGET,
    @SerialName("same_brand_only") SAME_BRAND_ONLY,
}

@Serializable
enum class ExplanationDepth {
    @SerialName("brief") BRIEF,
    @SerialName("detailed") DETAILED,
}

@Serializable
data class Profile(
    @SerialName("profile_id") val profileId: String,
    @SerialName("profile_type") val profileType: ProfileType,
    val tone: String = "标准",
    @SerialName("proactive_voice_threshold") val proactiveVoiceThreshold: VoiceThreshold,
    @SerialName("haptic_mode") val hapticMode: HapticMode,
    @SerialName("budget_limit") val budgetLimit: Double,
    @SerialName("delivery_priority") val deliveryPriority: DeliveryPriority,
    @SerialName("substitution_policy") val substitutionPolicy: SubstitutionPolicy,
    @SerialName("explanation_depth") val explanationDepth: ExplanationDepth,
)

// ─── Wearable ────────────────────────────────────────────────────────────────

@Serializable
enum class WearableMode {
    @SerialName("idle") IDLE,
    @SerialName("warning") WARNING,
    @SerialName("handover") HANDOVER,
    @SerialName("processing") PROCESSING,
    @SerialName("completed") COMPLETED,
    @SerialName("error") ERROR,
}

@Serializable
enum class WearableColor {
    @SerialName("navy") NAVY,
    @SerialName("blue") BLUE,
    @SerialName("yellow") YELLOW,
    @SerialName("green") GREEN,
    @SerialName("red") RED,
}

@Serializable
enum class HapticPattern {
    @SerialName("none") NONE,
    @SerialName("double_short") DOUBLE_SHORT,
    @SerialName("single_pulse") SINGLE_PULSE,
    @SerialName("three_beat") THREE_BEAT,
    @SerialName("soft_short") SOFT_SHORT,
    @SerialName("error_once") ERROR_ONCE,
}

@Serializable
data class Wearable(
    val connected: Boolean,
    val mode: WearableMode,
    val text: String = "",
    val color: WearableColor = WearableColor.NAVY,
    val haptic: HapticPattern = HapticPattern.NONE,
    @SerialName("command_id") val commandId: String = "",
    @SerialName("heart_rate") val heartRate: Int? = null,
    @SerialName("signal_confidence") val signalConfidence: Double? = null,
)

// ─── Service Order ───────────────────────────────────────────────────────────

@Serializable
data class ServiceItem(
    val sku: String,
    val name: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: Double,
    val subtotal: Double,
    val substitution: String? = null,
)

@Serializable
enum class BudgetStatus {
    @SerialName("within_budget") WITHIN_BUDGET,
    @SerialName("over_budget") OVER_BUDGET,
}

@Serializable
enum class ServiceOrderStatus {
    @SerialName("preview") PREVIEW,
    @SerialName("awaiting_confirmation") AWAITING_CONFIRMATION,
    @SerialName("submitted") SUBMITTED,
    @SerialName("blocked") BLOCKED,
    @SerialName("failed") FAILED,
}

@Serializable
data class ServiceOrder(
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("preview_id") val previewId: String,
    val items: List<ServiceItem> = emptyList(),
    val total: Double = 0.0,
    @SerialName("budget_limit") val budgetLimit: Double = 0.0,
    @SerialName("budget_status") val budgetStatus: BudgetStatus = BudgetStatus.WITHIN_BUDGET,
    @SerialName("delivery_window") val deliveryWindow: String = "",
    val status: ServiceOrderStatus = ServiceOrderStatus.PREVIEW,
    @SerialName("error_code") val errorCode: String? = null,
)

// ─── Interaction Output ──────────────────────────────────────────────────────

@Serializable
enum class OutputPriority {
    @SerialName("low") LOW,
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH,
    @SerialName("critical") CRITICAL,
}

@Serializable
data class InteractionOutput(
    @SerialName("message_id") val messageId: String,
    val priority: OutputPriority = OutputPriority.NORMAL,
    @SerialName("owner_surface") val ownerSurface: String = "mobile", // "mobile" | "vehicle_hmi" | "none"
    @SerialName("suppressed_surfaces") val suppressedSurfaces: List<String> = emptyList(),
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("requires_confirmation") val requiresConfirmation: Boolean = false,
    val conclusion: String = "",
)

// ─── Root World State ────────────────────────────────────────────────────────

@Serializable
data class WorldState(
    @SerialName("schema_version") val schemaVersion: String = "0.2.0",
    @SerialName("session_id") val sessionId: String = "",
    val revision: Int = 0,
    @SerialName("updated_at") val updatedAt: String = "",
    val stage: Stage = Stage.OFF_VEHICLE_IDLE,
    val scene: Scene = Scene.OFF_VEHICLE,
    @SerialName("primary_surface") val primarySurface: PrimarySurface = PrimarySurface.MOBILE,
    val risk: Risk = Risk(pressureLevel = PressureLevel.L0),
    val tasks: List<Task> = emptyList(),
    val eta: String? = null,
    val actions: List<Action> = emptyList(),
    val confirmation: Confirmation? = null,
    val profile: Profile? = null,
    val wearable: Wearable? = null,
    @SerialName("service_orders") val serviceOrders: List<ServiceOrder> = emptyList(),
    val output: InteractionOutput? = null,
    @SerialName("action_ledger") val actionLedger: List<String> = emptyList(),
    @SerialName("service_mock_mode") val serviceMockMode: String? = null,
)
