package com.pressureagent.mobile.data.mock

import com.pressureagent.mobile.domain.model.*

/**
 * Predefined world-state snapshots for the 6-week demo story.
 *
 * Canonical story line (v0.2):
 *   off_vehicle_idle → pre_departure_warning → handover_to_vehicle →
 *   vehicle_observation → takeover_L2 → planning →
 *   service_prepared → waiting_confirmation →
 *   executing → action_completed → cooldown → parked_review
 *
 * Each function produces a complete [WorldState] with a fresh [revision]
 * and [updatedAt] timestamp injected by the caller (MockAgent).
 */
object StoryScript {

    /** Total number of steps in the script. */
    const val STEP_COUNT = 9

    private fun defaultProfile() = Profile(
        profileId = "profile_default",
        profileType = ProfileType.EFFICIENCY,
        tone = "标准",
        proactiveVoiceThreshold = VoiceThreshold.L2,
        hapticMode = HapticMode.CLEAR,
        budgetLimit = 200.0,
        deliveryPriority = DeliveryPriority.FASTEST,
        substitutionPolicy = SubstitutionPolicy.SAME_SPEC_WITHIN_BUDGET,
        explanationDepth = ExplanationDepth.BRIEF,
    )

    private fun pickupTask(status: TaskStatus = TaskStatus.PENDING) = Task(
        taskId = "task_pickup_child",
        title = "接孩子",
        scheduledAt = "2026-07-16T18:10:00+08:00",
        location = "阳光小学",
        taskType = TaskType.RIGID,
        priority = Priority.HIGH,
        adjustable = false,
        status = status,
        waitingParty = listOf("老师", "家人"),
        capabilityTags = listOf("pickup", "rigid_deadline"),
    )

    private fun groceryTask(status: TaskStatus = TaskStatus.PENDING) = Task(
        taskId = "task_grocery",
        title = "超市采购",
        scheduledAt = "2026-07-16T19:30:00+08:00",
        taskType = TaskType.FLEXIBLE,
        priority = Priority.LOW,
        adjustable = true,
        status = status,
        waitingParty = emptyList(),
        capabilityTags = listOf("grocery", "delivery_eligible"),
    )

    /**
     * Step 0 — off_vehicle_idle.
     * No tasks, no risk, mobile is primary surface.
     */
    fun offVehicleIdle(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.OFF_VEHICLE_IDLE,
        scene = Scene.OFF_VEHICLE,
        primarySurface = PrimarySurface.MOBILE,
        risk = Risk(pressureLevel = PressureLevel.L0),
        tasks = emptyList(),
        actions = emptyList(),
        profile = defaultProfile(),
        wearable = Wearable(connected = false, mode = WearableMode.IDLE, text = ""),
        actionLedger = emptyList(),
    )

    /**
     * Step 1 — task_created (mapped to pre_departure_warning).
     * User has created two tasks via voice: 接孩子 (rigid) + 超市采购 (flexible).
     */
    fun taskCreated(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.PRE_DEPARTURE_WARNING,
        scene = Scene.OFF_VEHICLE,
        primarySurface = PrimarySurface.MOBILE,
        risk = Risk(pressureLevel = PressureLevel.L0),
        tasks = listOf(pickupTask(), groceryTask()),
        actions = emptyList(),
        profile = defaultProfile(),
        wearable = Wearable(connected = true, mode = WearableMode.IDLE, text = "任务已创建"),
        actionLedger = listOf("创建任务: 接孩子(刚性) + 超市采购(弹性)"),
    )

    /**
     * Step 2 — meeting overrun.
     * Meeting runs over — user reports delay. Risk elevated to L1.
     */
    fun meetingOverrun(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.PRE_DEPARTURE_WARNING,
        scene = Scene.OFF_VEHICLE,
        primarySurface = PrimarySurface.MOBILE,
        risk = Risk(
            pressureLevel = PressureLevel.L1,
            lateMinutes = 0,
            reasonCodes = listOf("meeting_overrun", "departure_delayed"),
            auxiliarySignals = emptyList(),
        ),
        tasks = listOf(pickupTask(), groceryTask()),
        actions = emptyList(),
        profile = defaultProfile(),
        wearable = Wearable(
            connected = true,
            mode = WearableMode.WARNING,
            text = "注意时间",
            color = WearableColor.YELLOW,
            haptic = HapticPattern.DOUBLE_SHORT,
        ),
        actionLedger = listOf(
            "创建任务: 接孩子(刚性) + 超市采购(弹性)",
            "会议延迟: 出发时间晚于计划",
        ),
    )

    /**
     * Step 3 — handover to vehicle.
     * User approaching/entering vehicle. primary_surface switches to vehicle_hmi.
     * Phone enters Companion read-only mode.
     */
    fun handoverToVehicle(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.HANDOVER_TO_VEHICLE,
        scene = Scene.APPROACHING_VEHICLE,
        primarySurface = PrimarySurface.VEHICLE_HMI,
        risk = Risk(
            pressureLevel = PressureLevel.L1,
            lateMinutes = 0,
            reasonCodes = listOf("meeting_overrun", "departure_window_narrowing"),
            auxiliarySignals = emptyList(),
        ),
        tasks = listOf(pickupTask(), groceryTask()),
        actions = emptyList(),
        profile = defaultProfile(),
        wearable = Wearable(
            connected = true,
            mode = WearableMode.HANDOVER,
            text = "已切换车机",
            color = WearableColor.BLUE,
            haptic = HapticPattern.SINGLE_PULSE,
        ),
        actionLedger = listOf(
            "创建任务: 接孩子(刚性) + 超市采购(弹性)",
            "会议延迟: 出发时间晚于计划",
            "交接车机: primary_surface → vehicle_hmi",
        ),
    )

    /**
     * Step 4 — vehicle_observation.
     * User driving towards 阳光小学. Agent observing.
     */
    fun vehicleObservation(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.VEHICLE_OBSERVATION,
        scene = Scene.DRIVING,
        primarySurface = PrimarySurface.VEHICLE_HMI,
        eta = "2026-07-16T18:10:00+08:00",
        risk = Risk(
            pressureLevel = PressureLevel.L1,
            lateMinutes = 0,
            reasonCodes = listOf("en_route", "eta_on_time"),
            auxiliarySignals = emptyList(),
        ),
        tasks = listOf(pickupTask(), groceryTask()),
        actions = emptyList(),
        profile = defaultProfile(),
        wearable = Wearable(
            connected = true,
            mode = WearableMode.IDLE,
            text = "前往阳光小学",
            color = WearableColor.NAVY,
            haptic = HapticPattern.NONE,
        ),
        actionLedger = listOf(
            "创建任务: 接孩子(刚性) + 超市采购(弹性)",
            "会议延迟: 出发时间晚于计划",
            "交接车机: primary_surface → vehicle_hmi",
        ),
    )

    /**
     * Step 5 — traffic delay → takeover_L2.
     * Traffic congestion detected. ETA pushed to 18:28 (18 min delay).
     * Agent evaluates → L2 coordination takeover.
     */
    fun takeoverL2(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.TAKEOVER_L2,
        scene = Scene.HIGH_LOAD_DRIVING,
        primarySurface = PrimarySurface.VEHICLE_HMI,
        eta = "2026-07-16T18:28:00+08:00",
        risk = Risk(
            pressureLevel = PressureLevel.L2,
            lateMinutes = 18,
            reasonCodes = listOf("traffic_congestion", "rigid_task_impacted", "eta_exceeds_deadline"),
            auxiliarySignals = listOf("user_asked_can_we_make_it"),
        ),
        tasks = listOf(pickupTask(), groceryTask()),
        actions = emptyList(),
        profile = defaultProfile(),
        output = InteractionOutput(
            messageId = "output_l2_warning",
            priority = OutputPriority.HIGH,
            ownerSurface = "vehicle_hmi",
            suppressedSurfaces = listOf("mobile", "wearable"),
            expiresAt = "2026-07-16T18:15:00+08:00",
            requiresConfirmation = false,
            conclusion = "预计晚到 18 分钟，继续加速无法明显缩短时间。",
        ),
        wearable = Wearable(
            connected = true,
            mode = WearableMode.WARNING,
            text = "预计晚到 18 分钟",
            color = WearableColor.YELLOW,
            haptic = HapticPattern.DOUBLE_SHORT,
            heartRate = 95,
            signalConfidence = 0.7,
        ),
        actionLedger = listOf(
            "创建任务: 接孩子(刚性) + 超市采购(弹性)",
            "会议延迟: 出发时间晚于计划",
            "交接车机: primary_surface → vehicle_hmi",
            "L2 接管: 交通拥堵，ETA 18:28（+18min）",
        ),
    )

    /**
     * Step 6 — planning.
     * Agent plans actions: reschedule grocery, draft messages, prepare service order.
     */
    fun planning(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.PLANNING,
        scene = Scene.HIGH_LOAD_DRIVING,
        primarySurface = PrimarySurface.VEHICLE_HMI,
        eta = "2026-07-16T18:28:00+08:00",
        risk = Risk(
            pressureLevel = PressureLevel.L2,
            lateMinutes = 18,
            reasonCodes = listOf("traffic_congestion", "rigid_task_impacted"),
            auxiliarySignals = listOf("user_asked_can_we_make_it"),
        ),
        tasks = listOf(
            pickupTask(),
            groceryTask(status = TaskStatus.RESCHEDULED),
        ),
        actions = listOf(
            Action(
                actionId = "action_reschedule_grocery",
                type = ActionType.RESCHEDULE,
                target = "超市采购",
                status = ActionStatus.COMPLETED,
                risk = ActionRiskLevel.LOW,
                requiresConfirmation = false,
                summary = "已顺延到接完孩子之后",
            ),
            Action(
                actionId = "action_notify_teacher",
                type = ActionType.MESSAGE,
                target = "王老师",
                status = ActionStatus.PLANNED,
                risk = ActionRiskLevel.HIGH,
                requiresConfirmation = true,
                summary = "通知老师预计晚到 18 分钟",
                detailsRef = "王老师您好，路上临时拥堵，预计 18:28 到达学校，麻烦您稍等一下，谢谢。",
            ),
            Action(
                actionId = "action_notify_family",
                type = ActionType.MESSAGE,
                target = "家人",
                status = ActionStatus.PLANNED,
                risk = ActionRiskLevel.MEDIUM,
                requiresConfirmation = true,
                summary = "同步晚到和接送状态",
                detailsRef = "路上有些拥堵，我预计 18:28 到学校，超市改到接完孩子后再去。",
            ),
        ),
        profile = defaultProfile(),
        output = InteractionOutput(
            messageId = "output_planning",
            priority = OutputPriority.HIGH,
            ownerSurface = "vehicle_hmi",
            suppressedSurfaces = listOf("mobile", "wearable"),
            expiresAt = "2026-07-16T18:15:00+08:00",
            requiresConfirmation = true,
            conclusion = "预计晚到 18 分钟。需要通知老师和家人。",
        ),
        wearable = Wearable(
            connected = true,
            mode = WearableMode.PROCESSING,
            text = "正在生成通知",
            color = WearableColor.BLUE,
            haptic = HapticPattern.THREE_BEAT,
        ),
        /* no service_orders yet — will be generated in next step */
        actionLedger = listOf(
            "创建任务: 接孩子(刚性) + 超市采购(弹性)",
            "会议延迟: 出发时间晚于计划",
            "交接车机: primary_surface → vehicle_hmi",
            "L2 接管: 交通拥堵，ETA 18:28（+18min）",
            "规划动作: 顺延超市 + 草拟老师/家人消息",
        ),
    )

    /**
     * Step 7 — waiting_confirmation（核心 Demo 状态）.
     * Messages drafted + service order preview ready, waiting for user confirmation.
     */
    fun waitingConfirmation(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.WAITING_CONFIRMATION,
        scene = Scene.HIGH_LOAD_DRIVING,
        primarySurface = PrimarySurface.VEHICLE_HMI,
        eta = "2026-07-16T18:28:00+08:00",
        risk = Risk(
            pressureLevel = PressureLevel.L2,
            lateMinutes = 18,
            reasonCodes = listOf("traffic_congestion", "rigid_task_impacted"),
            auxiliarySignals = listOf("user_asked_can_we_make_it"),
        ),
        tasks = listOf(
            pickupTask(),
            groceryTask(status = TaskStatus.RESCHEDULED),
        ),
        actions = listOf(
            Action(
                actionId = "action_reschedule_grocery",
                type = ActionType.RESCHEDULE,
                target = "超市采购",
                status = ActionStatus.COMPLETED,
                risk = ActionRiskLevel.LOW,
                requiresConfirmation = false,
                summary = "已顺延到接完孩子之后",
            ),
            Action(
                actionId = "action_notify_teacher",
                type = ActionType.MESSAGE,
                target = "王老师",
                status = ActionStatus.AWAITING_CONFIRMATION,
                risk = ActionRiskLevel.HIGH,
                requiresConfirmation = true,
                summary = "通知老师预计晚到 18 分钟",
                detailsRef = "王老师您好，路上临时拥堵，预计 18:28 到达学校，麻烦您稍等一下，谢谢。",
            ),
            Action(
                actionId = "action_notify_family",
                type = ActionType.MESSAGE,
                target = "家人",
                status = ActionStatus.AWAITING_CONFIRMATION,
                risk = ActionRiskLevel.MEDIUM,
                requiresConfirmation = true,
                summary = "同步晚到和接送状态",
                detailsRef = "路上有些拥堵，我预计 18:28 到学校，超市改到接完孩子后再去。",
            ),
        ),
        confirmation = Confirmation(
            confirmationId = "confirm_send_001",
            actionIds = listOf("action_notify_teacher", "action_notify_family"),
            expiresAt = "2026-07-16T18:20:00+08:00",
            status = ConfirmationStatus.PENDING,
            ownerSurface = "vehicle_hmi",
        ),
        profile = defaultProfile(),
        output = InteractionOutput(
            messageId = "output_confirm",
            priority = OutputPriority.CRITICAL,
            ownerSurface = "vehicle_hmi",
            suppressedSurfaces = listOf("mobile", "wearable"),
            expiresAt = "2026-07-16T18:20:00+08:00",
            requiresConfirmation = true,
            conclusion = "老师和家人的消息已备好，是否发送？",
        ),
        serviceOrders = listOf(
            ServiceOrder(
                orderId = null,
                previewId = "preview_grocery_001",
                items = listOf(
                    ServiceItem(sku = "milk_1L", name = "鲜牛奶 1L", quantity = 2, unitPrice = 15.9, subtotal = 31.8),
                    ServiceItem(sku = "bread_loaf", name = "全麦面包", quantity = 1, unitPrice = 12.0, subtotal = 12.0),
                    ServiceItem(sku = "egg_10pk", name = "鸡蛋 10枚装", quantity = 1, unitPrice = 22.5, subtotal = 22.5),
                    ServiceItem(sku = "fruit_mix", name = "时令水果拼盘", quantity = 1, unitPrice = 35.0, subtotal = 35.0, substitution = "同价位应季水果"),
                ),
                total = 101.3,
                budgetLimit = 200.0,
                budgetStatus = BudgetStatus.WITHIN_BUDGET,
                deliveryWindow = "18:40-19:10",
                status = ServiceOrderStatus.PREVIEW,
            ),
        ),
        wearable = Wearable(
            connected = true,
            mode = WearableMode.PROCESSING,
            text = "等待确认",
            color = WearableColor.BLUE,
            haptic = HapticPattern.THREE_BEAT,
        ),
        actionLedger = listOf(
            "创建任务: 接孩子(刚性) + 超市采购(弹性)",
            "会议延迟: 出发时间晚于计划",
            "交接车机: primary_surface → vehicle_hmi",
            "L2 接管: 交通拥堵，ETA 18:28（+18min）",
            "规划动作: 顺延超市 + 草拟老师/家人消息",
            "服务方案: 超市配送 4项 ¥101.3，配送 18:40-19:10",
        ),
        serviceMockMode = "success",
    )

    /**
     * Step 8 — action_completed.
     * User confirmed. Messages "sent". Agent resolved.
     */
    fun actionCompleted(sessionId: String, revision: Int, updatedAt: String): WorldState = WorldState(
        sessionId = sessionId,
        revision = revision,
        updatedAt = updatedAt,
        stage = Stage.ACTION_COMPLETED,
        scene = Scene.DRIVING,
        primarySurface = PrimarySurface.VEHICLE_HMI,
        eta = "2026-07-16T18:28:00+08:00",
        risk = Risk(
            pressureLevel = PressureLevel.L1,
            lateMinutes = 18,
            reasonCodes = listOf("traffic_congestion", "notified_teacher_and_family"),
            auxiliarySignals = emptyList(),
        ),
        tasks = listOf(
            pickupTask(),
            groceryTask(status = TaskStatus.RESCHEDULED),
        ),
        actions = listOf(
            Action(
                actionId = "action_reschedule_grocery",
                type = ActionType.RESCHEDULE,
                target = "超市采购",
                status = ActionStatus.COMPLETED,
                risk = ActionRiskLevel.LOW,
                requiresConfirmation = false,
                summary = "已顺延到接完孩子之后",
            ),
            Action(
                actionId = "action_notify_teacher",
                type = ActionType.MESSAGE,
                target = "王老师",
                status = ActionStatus.COMPLETED,
                risk = ActionRiskLevel.HIGH,
                requiresConfirmation = true,
                summary = "已通知老师预计晚到 18 分钟 (模拟)",
            ),
            Action(
                actionId = "action_notify_family",
                type = ActionType.MESSAGE,
                target = "家人",
                status = ActionStatus.COMPLETED,
                risk = ActionRiskLevel.MEDIUM,
                requiresConfirmation = true,
                summary = "已同步晚到和接送状态 (模拟)",
            ),
            Action(
                actionId = "action_grocery_order",
                type = ActionType.SERVICE_ORDER,
                target = "超市配送",
                status = ActionStatus.COMPLETED,
                risk = ActionRiskLevel.LOW,
                requiresConfirmation = false,
                summary = "超市配送订单已提交 (模拟) — 4项 ¥101.3",
            ),
        ),
        confirmation = Confirmation(
            confirmationId = "confirm_send_001",
            actionIds = listOf("action_notify_teacher", "action_notify_family"),
            expiresAt = "2026-07-16T18:20:00+08:00",
            status = ConfirmationStatus.ACCEPTED,
            confirmedBy = "vehicle_hmi",
            ownerSurface = "vehicle_hmi",
        ),
        profile = defaultProfile(),
        output = InteractionOutput(
            messageId = "output_done",
            priority = OutputPriority.NORMAL,
            ownerSurface = "vehicle_hmi",
            suppressedSurfaces = listOf("wearable"),
            expiresAt = "2026-07-16T18:30:00+08:00",
            requiresConfirmation = false,
            conclusion = "消息已发送（模拟），预计 18:28 到达学校。超市配送 18:40-19:10 送达。",
        ),
        serviceOrders = listOf(
            ServiceOrder(
                orderId = "order_grocery_001",
                previewId = "preview_grocery_001",
                items = listOf(
                    ServiceItem(sku = "milk_1L", name = "鲜牛奶 1L", quantity = 2, unitPrice = 15.9, subtotal = 31.8),
                    ServiceItem(sku = "bread_loaf", name = "全麦面包", quantity = 1, unitPrice = 12.0, subtotal = 12.0),
                    ServiceItem(sku = "egg_10pk", name = "鸡蛋 10枚装", quantity = 1, unitPrice = 22.5, subtotal = 22.5),
                    ServiceItem(sku = "fruit_mix", name = "时令水果拼盘", quantity = 1, unitPrice = 35.0, subtotal = 35.0),
                ),
                total = 101.3,
                budgetLimit = 200.0,
                budgetStatus = BudgetStatus.WITHIN_BUDGET,
                deliveryWindow = "18:40-19:10",
                status = ServiceOrderStatus.SUBMITTED,
            ),
        ),
        wearable = Wearable(
            connected = true,
            mode = WearableMode.COMPLETED,
            text = "消息已发送",
            color = WearableColor.GREEN,
            haptic = HapticPattern.SOFT_SHORT,
        ),
        actionLedger = listOf(
            "创建任务: 接孩子(刚性) + 超市采购(弹性)",
            "会议延迟: 出发时间晚于计划",
            "交接车机: primary_surface → vehicle_hmi",
            "L2 接管: 交通拥堵，ETA 18:28（+18min）",
            "规划动作: 顺延超市 + 草拟老师/家人消息",
            "服务方案: 超市配送 4项 ¥101.3",
            "已确认: 消息已发送（模拟），订单已提交（模拟）",
        ),
        serviceMockMode = "success",
    )

    // ─── Step factory ────────────────────────────────────────────────────────

    /** Return the snapshot for [step] with the given params. */
    fun forStep(step: Int, sessionId: String, revision: Int, updatedAt: String): WorldState = when (step) {
        0 -> offVehicleIdle(sessionId, revision, updatedAt)
        1 -> taskCreated(sessionId, revision, updatedAt)
        2 -> meetingOverrun(sessionId, revision, updatedAt)
        3 -> handoverToVehicle(sessionId, revision, updatedAt)
        4 -> vehicleObservation(sessionId, revision, updatedAt)
        5 -> takeoverL2(sessionId, revision, updatedAt)
        6 -> planning(sessionId, revision, updatedAt)
        7 -> waitingConfirmation(sessionId, revision, updatedAt)
        8 -> actionCompleted(sessionId, revision, updatedAt)
        else -> offVehicleIdle(sessionId, revision, updatedAt)
    }
}
