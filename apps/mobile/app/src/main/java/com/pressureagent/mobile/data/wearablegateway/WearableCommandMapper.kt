package com.pressureagent.mobile.data.wearablegateway

import com.pressureagent.mobile.domain.model.HapticPattern
import com.pressureagent.mobile.domain.model.WearableColor
import com.pressureagent.mobile.domain.model.WearableMode
import com.pressureagent.mobile.domain.model.WorldState

object WearableCommandMapper {
    fun toWatchCommand(worldState: WorldState): WatchSetStateCommand? {
        val wearable = worldState.wearable ?: return null
        val commandId = wearable.commandId.ifBlank {
            "world-${worldState.sessionId}-${worldState.revision}"
        }

        return toWatchCommand(
            commandId = commandId,
            mode = wearable.mode,
            text = wearable.text,
            color = wearable.color,
            haptic = wearable.haptic,
            source = "android-gateway",
        )
    }

    fun toWatchCommand(
        commandId: String,
        mode: WearableMode,
        text: String = "",
        color: WearableColor = WearableColor.NAVY,
        haptic: HapticPattern = HapticPattern.NONE,
        source: String = "android-gateway",
    ): WatchSetStateCommand {
        val serialMode = mode.serialValue()
        return WatchSetStateCommand(
            commandId = commandId,
            mode = serialMode,
            icon = iconFor(mode),
            title = titleFor(mode),
            text = text.ifBlank { textFor(mode) },
            color = colorFor(color, mode),
            dimColor = dimColorFor(color, mode),
            haptic = hapticFor(haptic),
            source = source,
        )
    }

    private fun WearableMode.serialValue(): String = when (this) {
        WearableMode.IDLE -> "idle"
        WearableMode.WARNING -> "warning"
        WearableMode.HANDOVER -> "handover"
        WearableMode.PROCESSING -> "processing"
        WearableMode.COMPLETED -> "completed"
        WearableMode.ERROR -> "error"
    }

    private fun iconFor(mode: WearableMode): String = when (mode) {
        WearableMode.IDLE -> "A"
        WearableMode.WARNING -> "!"
        WearableMode.HANDOVER -> ">"
        WearableMode.PROCESSING -> "..."
        WearableMode.COMPLETED -> "OK"
        WearableMode.ERROR -> "X"
    }

    private fun titleFor(mode: WearableMode): String = when (mode) {
        WearableMode.IDLE -> "AURI 已就绪"
        WearableMode.WARNING -> "风险提醒"
        WearableMode.HANDOVER -> "进入驾驶模式"
        WearableMode.PROCESSING -> "接管处理中"
        WearableMode.COMPLETED -> "已完成"
        WearableMode.ERROR -> "请看手机"
    }

    private fun textFor(mode: WearableMode): String = when (mode) {
        WearableMode.IDLE -> "等待手机同步"
        WearableMode.WARNING -> "请关注接管准备"
        WearableMode.HANDOVER -> "车机负责确认"
        WearableMode.PROCESSING -> "AURI 正在协调"
        WearableMode.COMPLETED -> "保持当前节奏"
        WearableMode.ERROR -> "连接或数据异常"
    }

    private fun colorFor(color: WearableColor, mode: WearableMode): Int = when (color) {
        WearableColor.BLUE -> 0x2f6bff
        WearableColor.YELLOW -> 0xe6a700
        WearableColor.GREEN -> 0x2e9d6f
        WearableColor.RED -> 0xd1495b
        WearableColor.NAVY -> when (mode) {
            WearableMode.WARNING -> 0xe6a700
            WearableMode.COMPLETED -> 0x2e9d6f
            WearableMode.ERROR -> 0xd1495b
            else -> 0x2f6bff
        }
    }

    private fun dimColorFor(color: WearableColor, mode: WearableMode): Int = when (color) {
        WearableColor.BLUE -> 0x132c66
        WearableColor.YELLOW -> 0x4d3b0b
        WearableColor.GREEN -> 0x123d2d
        WearableColor.RED -> 0x4d1821
        WearableColor.NAVY -> when (mode) {
            WearableMode.WARNING -> 0x4d3b0b
            WearableMode.COMPLETED -> 0x123d2d
            WearableMode.ERROR -> 0x4d1821
            else -> 0x132c66
        }
    }

    private fun hapticFor(haptic: HapticPattern): String = when (haptic) {
        HapticPattern.NONE -> "none"
        HapticPattern.DOUBLE_SHORT -> "double_short"
        HapticPattern.SINGLE_PULSE -> "single_pulse"
        HapticPattern.THREE_BEAT -> "three_beat"
        HapticPattern.SOFT_SHORT -> "soft_short"
        HapticPattern.ERROR_ONCE -> "error_once"
    }
}
