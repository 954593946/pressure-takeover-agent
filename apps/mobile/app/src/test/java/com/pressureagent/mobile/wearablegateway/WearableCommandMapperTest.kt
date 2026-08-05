package com.pressureagent.mobile.data.wearablegateway

import com.pressureagent.mobile.domain.model.HapticPattern
import com.pressureagent.mobile.domain.model.PressureLevel
import com.pressureagent.mobile.domain.model.Risk
import com.pressureagent.mobile.domain.model.Stage
import com.pressureagent.mobile.domain.model.Wearable
import com.pressureagent.mobile.domain.model.WearableColor
import com.pressureagent.mobile.domain.model.WearableMode
import com.pressureagent.mobile.domain.model.WorldState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WearableCommandMapperTest {
    @Test
    fun preservesContractHapticNamesForWatchCommand() {
        val command = WearableCommandMapper.toWatchCommand(
            commandId = "cmd-123",
            mode = WearableMode.PROCESSING,
            text = "正在生成通知",
            color = WearableColor.BLUE,
            haptic = HapticPattern.THREE_BEAT,
        )

        assertEquals("cmd-123", command.commandId)
        assertEquals("processing", command.mode)
        assertEquals("three_beat", command.haptic)
        assertEquals(0x2f6bff, command.color)
    }

    @Test
    fun usesStableWorldRevisionCommandIdWhenBackendCommandIdIsBlank() {
        val command = WearableCommandMapper.toWatchCommand(
            WorldState(
                sessionId = "session-a",
                revision = 12,
                stage = Stage.PRE_DEPARTURE_WARNING,
                risk = Risk(pressureLevel = PressureLevel.L1),
                wearable = Wearable(
                    connected = true,
                    mode = WearableMode.WARNING,
                    text = "预计晚到 18 分钟",
                    color = WearableColor.YELLOW,
                    haptic = HapticPattern.DOUBLE_SHORT,
                    commandId = "",
                ),
            ),
        )

        assertNotNull(command)
        assertEquals("world-session-a-12", command.commandId)
        assertEquals("warning", command.mode)
        assertEquals("double_short", command.haptic)
        assertEquals(0xe6a700, command.color)
    }

    @Test
    fun mapsTaskCreatedPreWarningL0ToSilentIdle() {
        val command = WearableCommandMapper.toWatchCommand(
            WorldState(
                sessionId = "demo",
                revision = 1,
                stage = Stage.PRE_DEPARTURE_WARNING,
                risk = Risk(pressureLevel = PressureLevel.L0),
                wearable = Wearable(
                    connected = true,
                    mode = WearableMode.WARNING,
                    text = "任务已创建",
                    color = WearableColor.YELLOW,
                    haptic = HapticPattern.DOUBLE_SHORT,
                ),
            ),
        )

        assertNotNull(command)
        assertEquals("idle", command.mode)
        assertEquals("none", command.haptic)
        assertEquals(0x2f6bff, command.color)
    }

    @Test
    fun mapsL1PreDepartureWarningToYellowDoubleShort() {
        val command = WearableCommandMapper.toWatchCommand(
            WorldState(
                sessionId = "demo",
                revision = 2,
                stage = Stage.PRE_DEPARTURE_WARNING,
                risk = Risk(pressureLevel = PressureLevel.L1),
                wearable = null,
            ),
        )

        assertNotNull(command)
        assertEquals("world-demo-2", command.commandId)
        assertEquals("warning", command.mode)
        assertEquals("出发窗口收紧", command.text)
        assertEquals("double_short", command.haptic)
        assertEquals(0xe6a700, command.color)
    }

    @Test
    fun mapsDrivingAndPlanningStagesToAgentDrivenHaptics() {
        listOf(
            Triple(Stage.HANDOVER_TO_VEHICLE, "handover", "single_pulse"),
            Triple(Stage.VEHICLE_OBSERVATION, "handover", "single_pulse"),
            Triple(Stage.TAKEOVER_L2, "processing", "three_beat"),
            Triple(Stage.PLANNING, "processing", "three_beat"),
            Triple(Stage.WAITING_CONFIRMATION, "processing", "three_beat"),
        ).forEachIndexed { index, (stage, expectedMode, expectedHaptic) ->
            val command = WearableCommandMapper.toWatchCommand(
                WorldState(
                    sessionId = "demo",
                    revision = 10 + index,
                    stage = stage,
                    wearable = Wearable(
                        connected = true,
                        mode = WearableMode.WARNING,
                        text = "预计晚到 18 分钟",
                        color = WearableColor.YELLOW,
                        haptic = HapticPattern.DOUBLE_SHORT,
                    ),
                ),
            )

            assertNotNull(command)
            assertEquals(expectedMode, command.mode)
            assertEquals(expectedHaptic, command.haptic)
            assertEquals(0x2f6bff, command.color)
        }
    }

    @Test
    fun mapsCompletedAndCooldownToGreenSoftShortBeforeRecoveryIdle() {
        val completed = WearableCommandMapper.toWatchCommand(
            WorldState(
                sessionId = "demo",
                revision = 20,
                stage = Stage.ACTION_COMPLETED,
                wearable = null,
            ),
        )
        val cooldown = WearableCommandMapper.toWatchCommand(
            WorldState(
                sessionId = "demo",
                revision = 21,
                stage = Stage.COOLDOWN,
                wearable = null,
            ),
        )
        val parked = WearableCommandMapper.toWatchCommand(
            WorldState(
                sessionId = "demo",
                revision = 22,
                stage = Stage.PARKED_REVIEW,
                wearable = null,
            ),
        )

        assertNotNull(completed)
        assertEquals("completed", completed.mode)
        assertEquals("soft_short", completed.haptic)
        assertEquals(0x2e9d6f, completed.color)

        assertNotNull(cooldown)
        assertEquals("completed", cooldown.mode)
        assertEquals("已处理", cooldown.text)
        assertEquals("soft_short", cooldown.haptic)
        assertEquals(0x2e9d6f, cooldown.color)

        assertNotNull(parked)
        assertEquals("idle", parked.mode)
        assertEquals("手机复盘", parked.text)
        assertEquals("none", parked.haptic)
    }
}
