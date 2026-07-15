package com.pressureagent.mobile.data.mock

import com.pressureagent.mobile.domain.model.*

/**
 * Predefined world-state snapshots for the 6-week demo story.
 *
 * Canonical story line:
 *   idle → task_created → meeting_delay → departure_warning →
 *   vehicle_mode → traffic_delay → pressure_takeover →
 *   waiting_confirmation → action_completed
 *
 * Each function produces a complete [WorldState] with a fresh [revision]
 * and [updatedAt] timestamp injected by the caller (MockAgent).
 */
object StoryScript {

    /** Total number of steps in the script. */
    const val STEP_COUNT = 9

    /**
     * Step 0 — idle.
     * No tasks, no risk, vehicle off, wearable disconnected.
     */
    fun idle(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.IDLE,
        agentMode = AgentMode.QUIET,
        tasks = emptyList(),
        vehicle = Vehicle(mode = VehicleMode.OFF, delayMinutes = 0),
        risk = Risk(RiskLevel.NONE, realityRiskConfirmed = false, auxiliarySignalPresent = false, reasons = emptyList()),
        wearable = Wearable(connected = false, state = WearableState.IDLE, vibration = Vibration.NONE),
        actions = emptyList(),
        messages = emptyList(),
    )

    /**
     * Step 1 — task_created.
     * User has created two tasks: 接孩子 (rigid) + 超市采购 (flexible).
     */
    fun taskCreated(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.TASK_CREATED,
        agentMode = AgentMode.OBSERVING,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.PENDING,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(mode = VehicleMode.OFF, delayMinutes = 0),
        risk = Risk(RiskLevel.NONE, realityRiskConfirmed = false, auxiliarySignalPresent = false, reasons = emptyList()),
        wearable = Wearable(connected = true, state = WearableState.IDLE, vibration = Vibration.NONE),
        actions = emptyList(),
        messages = emptyList(),
    )

    /**
     * Step 2 — meeting_delay.
     * Meeting runs over — user reports delay. Risk elevated to watch.
     */
    fun meetingDelay(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.MEETING_DELAY,
        agentMode = AgentMode.OBSERVING,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.PENDING,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(mode = VehicleMode.OFF, delayMinutes = 0),
        risk = Risk(
            level = RiskLevel.WATCH,
            realityRiskConfirmed = false,
            auxiliarySignalPresent = false,
            reasons = listOf("会议延迟，出发时间晚于计划"),
        ),
        wearable = Wearable(connected = true, state = WearableState.IDLE, vibration = Vibration.NONE),
        actions = emptyList(),
        messages = emptyList(),
    )

    /**
     * Step 3 — departure_warning.
     * System warns: latest departure time approaching.
     */
    fun departureWarning(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.DEPARTURE_WARNING,
        agentMode = AgentMode.OBSERVING,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.PENDING,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(mode = VehicleMode.OFF, delayMinutes = 0),
        risk = Risk(
            level = RiskLevel.WATCH,
            realityRiskConfirmed = false,
            auxiliarySignalPresent = false,
            reasons = listOf("会议延迟", "距离最晚出发时间还有 15 分钟"),
        ),
        wearable = Wearable(connected = true, state = WearableState.READY, vibration = Vibration.SHORT),
        actions = emptyList(),
        messages = emptyList(),
    )

    /**
     * Step 4 — vehicle_mode.
     * User enters vehicle, starts driving towards 阳光小学.
     */
    fun vehicleMode(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.VEHICLE_MODE,
        agentMode = AgentMode.OBSERVING,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.PENDING,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(
            mode = VehicleMode.DRIVING,
            destination = "阳光小学",
            eta = "2026-07-14T18:10:00+08:00",
            delayMinutes = 0,
        ),
        risk = Risk(
            level = RiskLevel.WATCH,
            realityRiskConfirmed = false,
            auxiliarySignalPresent = false,
            reasons = listOf("已出发，当前 ETA 18:10"),
        ),
        wearable = Wearable(
            connected = true,
            state = WearableState.DRIVING,
            text = "前往阳光小学",
            vibration = Vibration.NONE,
        ),
        actions = emptyList(),
        messages = emptyList(),
    )

    /**
     * Step 5 — traffic_delay.
     * Traffic congestion detected. ETA pushed to 18:28 (18 min delay).
     */
    fun trafficDelay(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.TRAFFIC_DELAY,
        agentMode = AgentMode.OBSERVING,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.PENDING,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(
            mode = VehicleMode.DRIVING,
            destination = "阳光小学",
            eta = "2026-07-14T18:28:00+08:00",
            delayMinutes = 18,
        ),
        risk = Risk(
            level = RiskLevel.HIGH,
            realityRiskConfirmed = true,
            auxiliarySignalPresent = false,
            reasons = listOf("刚性任务预计晚到 18 分钟"),
        ),
        wearable = Wearable(
            connected = true,
            state = WearableState.WARNING,
            text = "预计晚到 18 分钟",
            vibration = Vibration.SHORT,
            heartRate = 95,
            heartRateSource = HeartRateSource.SIMULATED,
        ),
        actions = emptyList(),
        messages = emptyList(),
    )

    /**
     * Step 6 — pressure_takeover.
     * Agent evaluates reality risk + user pressure, takes over.
     * Reschedules grocery, plans notifications.
     */
    fun pressureTakeover(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.PRESSURE_TAKEOVER,
        agentMode = AgentMode.TAKING_OVER,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.RESCHEDULED,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(
            mode = VehicleMode.DRIVING,
            destination = "阳光小学",
            eta = "2026-07-14T18:28:00+08:00",
            delayMinutes = 18,
        ),
        risk = Risk(
            level = RiskLevel.HIGH,
            realityRiskConfirmed = true,
            auxiliarySignalPresent = true,
            reasons = listOf("刚性任务预计晚到 18 分钟", "用户主动询问是否来得及"),
        ),
        wearable = Wearable(
            connected = true,
            state = WearableState.TAKING_OVER,
            text = "正在生成通知消息",
            vibration = Vibration.RHYTHMIC,
            heartRate = 105,
            heartRateSource = HeartRateSource.SIMULATED,
        ),
        actions = listOf(
            Action(
                id = "action_reschedule_grocery",
                type = ActionType.RESCHEDULE,
                target = "超市采购",
                summary = "已顺延到接完孩子之后",
                riskLevel = ActionRiskLevel.LOW,
                requiresConfirmation = false,
                status = ActionStatus.COMPLETED,
            ),
            Action(
                id = "action_notify_teacher",
                type = ActionType.SEND_MESSAGE,
                target = "老师",
                summary = "通知预计晚到 18 分钟",
                riskLevel = ActionRiskLevel.HIGH,
                requiresConfirmation = true,
                status = ActionStatus.PLANNED,
            ),
            Action(
                id = "action_notify_family",
                type = ActionType.SEND_MESSAGE,
                target = "家人",
                summary = "同步晚到和接送状态",
                riskLevel = ActionRiskLevel.MEDIUM,
                requiresConfirmation = true,
                status = ActionStatus.PLANNED,
            ),
        ),
        messages = emptyList(),
        conclusion = "预计晚到 18 分钟，继续加速无法明显缩短时间。需要通知老师和家人。",
    )

    /**
     * Step 7 — waiting_confirmation.
     * Messages drafted, waiting for user confirmation.
     * This is the key demo state (matches examples/world-state.json).
     */
    fun waitingConfirmation(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.WAITING_CONFIRMATION,
        agentMode = AgentMode.AWAITING_CONFIRMATION,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.RESCHEDULED,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(
            mode = VehicleMode.DRIVING,
            destination = "阳光小学",
            eta = "2026-07-14T18:28:00+08:00",
            delayMinutes = 18,
        ),
        risk = Risk(
            level = RiskLevel.HIGH,
            realityRiskConfirmed = true,
            auxiliarySignalPresent = true,
            reasons = listOf("刚性任务预计晚到 18 分钟", "用户主动询问是否来得及"),
        ),
        wearable = Wearable(
            connected = true,
            state = WearableState.TAKING_OVER,
            text = "消息已备好，等待确认",
            vibration = Vibration.RHYTHMIC,
            heartRate = 105,
            heartRateSource = HeartRateSource.SIMULATED,
        ),
        actions = listOf(
            Action(
                id = "action_reschedule_grocery",
                type = ActionType.RESCHEDULE,
                target = "超市采购",
                summary = "已顺延到接完孩子之后",
                riskLevel = ActionRiskLevel.LOW,
                requiresConfirmation = false,
                status = ActionStatus.COMPLETED,
            ),
            Action(
                id = "action_notify_teacher",
                type = ActionType.SEND_MESSAGE,
                target = "老师",
                summary = "通知预计晚到 18 分钟",
                riskLevel = ActionRiskLevel.HIGH,
                requiresConfirmation = true,
                status = ActionStatus.AWAITING_CONFIRMATION,
            ),
            Action(
                id = "action_notify_family",
                type = ActionType.SEND_MESSAGE,
                target = "家人",
                summary = "同步晚到和接送状态",
                riskLevel = ActionRiskLevel.MEDIUM,
                requiresConfirmation = true,
                status = ActionStatus.AWAITING_CONFIRMATION,
            ),
        ),
        messages = listOf(
            Message(
                id = "message_teacher",
                audience = Audience.TEACHER,
                displayName = "王老师",
                body = "王老师您好，路上临时拥堵，预计 18:28 到达学校，麻烦您稍等一下，谢谢。",
                status = MessageStatus.AWAITING_CONFIRMATION,
            ),
            Message(
                id = "message_family",
                audience = Audience.FAMILY,
                displayName = "家人",
                body = "路上有些拥堵，我预计 18:28 到学校，超市改到接完孩子后再去。",
                status = MessageStatus.AWAITING_CONFIRMATION,
            ),
        ),
        conclusion = "预计晚到 18 分钟，继续加速无法明显缩短时间。",
        confirmation = Confirmation(
            id = "confirm_send_001",
            prompt = "老师和家人的消息已备好，是否发送？",
            status = ConfirmationStatus.PENDING,
            actionIds = listOf("action_notify_teacher", "action_notify_family"),
        ),
    )

    /**
     * Step 8 — action_completed.
     * User confirmed. Messages "sent". Agent resolved.
     */
    fun actionCompleted(revision: Int, updatedAt: String): WorldState = WorldState(
        schemaVersion = "0.1.0",
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.ACTION_COMPLETED,
        agentMode = AgentMode.RESOLVED,
        tasks = listOf(
            Task(
                id = "task_pickup_child",
                title = "接孩子",
                scheduledAt = "2026-07-14T18:10:00+08:00",
                location = "阳光小学",
                type = TaskType.RIGID,
                priority = Priority.HIGH,
                adjustable = false,
                status = TaskStatus.PENDING,
                waitingParties = listOf("老师", "家人"),
            ),
            Task(
                id = "task_grocery",
                title = "超市采购",
                scheduledAt = "2026-07-14T19:30:00+08:00",
                type = TaskType.FLEXIBLE,
                priority = Priority.LOW,
                adjustable = true,
                status = TaskStatus.RESCHEDULED,
                waitingParties = emptyList(),
            ),
        ),
        vehicle = Vehicle(
            mode = VehicleMode.DRIVING,
            destination = "阳光小学",
            eta = "2026-07-14T18:28:00+08:00",
            delayMinutes = 18,
        ),
        risk = Risk(
            level = RiskLevel.WATCH,
            realityRiskConfirmed = true,
            auxiliarySignalPresent = true,
            reasons = listOf("刚性任务预计晚到 18 分钟", "已通知老师和家人"),
        ),
        wearable = Wearable(
            connected = true,
            state = WearableState.RESOLVED,
            text = "消息已发送",
            vibration = Vibration.NONE,
        ),
        actions = listOf(
            Action(
                id = "action_reschedule_grocery",
                type = ActionType.RESCHEDULE,
                target = "超市采购",
                summary = "已顺延到接完孩子之后",
                riskLevel = ActionRiskLevel.LOW,
                requiresConfirmation = false,
                status = ActionStatus.COMPLETED,
            ),
            Action(
                id = "action_notify_teacher",
                type = ActionType.SEND_MESSAGE,
                target = "老师",
                summary = "通知预计晚到 18 分钟",
                riskLevel = ActionRiskLevel.HIGH,
                requiresConfirmation = true,
                status = ActionStatus.COMPLETED,
            ),
            Action(
                id = "action_notify_family",
                type = ActionType.SEND_MESSAGE,
                target = "家人",
                summary = "同步晚到和接送状态",
                riskLevel = ActionRiskLevel.MEDIUM,
                requiresConfirmation = true,
                status = ActionStatus.COMPLETED,
            ),
        ),
        messages = listOf(
            Message(
                id = "message_teacher",
                audience = Audience.TEACHER,
                displayName = "王老师",
                body = "王老师您好，路上临时拥堵，预计 18:28 到达学校，麻烦您稍等一下，谢谢。",
                status = MessageStatus.SIMULATED_SENT,
            ),
            Message(
                id = "message_family",
                audience = Audience.FAMILY,
                displayName = "家人",
                body = "路上有些拥堵，我预计 18:28 到学校，超市改到接完孩子后再去。",
                status = MessageStatus.SIMULATED_SENT,
            ),
        ),
        conclusion = "消息已发送，预计 18:28 到达学校。",
        confirmation = Confirmation(
            id = "confirm_send_001",
            prompt = "老师和家人的消息已备好，是否发送？",
            status = ConfirmationStatus.ACCEPTED,
            actionIds = listOf("action_notify_teacher", "action_notify_family"),
        ),
    )

    // ─── Step factory ────────────────────────────────────────────────────────

    /** Return the snapshot for [step] with the given [revision] and [updatedAt]. */
    fun forStep(step: Int, revision: Int, updatedAt: String): WorldState = when (step) {
        0 -> idle(revision, updatedAt)
        1 -> taskCreated(revision, updatedAt)
        2 -> meetingDelay(revision, updatedAt)
        3 -> departureWarning(revision, updatedAt)
        4 -> vehicleMode(revision, updatedAt)
        5 -> trafficDelay(revision, updatedAt)
        6 -> pressureTakeover(revision, updatedAt)
        7 -> waitingConfirmation(revision, updatedAt)
        8 -> actionCompleted(revision, updatedAt)
        else -> idle(revision, updatedAt)
    }
}
