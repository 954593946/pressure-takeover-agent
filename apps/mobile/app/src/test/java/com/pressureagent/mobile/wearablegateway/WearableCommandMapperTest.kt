package com.pressureagent.mobile.data.wearablegateway

import com.pressureagent.mobile.domain.model.HapticPattern
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
            WorldState(
                sessionId = "demo",
                revision = 7,
                wearable = Wearable(
                    connected = true,
                    mode = WearableMode.PROCESSING,
                    text = "正在生成通知",
                    color = WearableColor.BLUE,
                    haptic = HapticPattern.THREE_BEAT,
                    commandId = "cmd-123",
                ),
            ),
        )

        assertNotNull(command)
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
}
