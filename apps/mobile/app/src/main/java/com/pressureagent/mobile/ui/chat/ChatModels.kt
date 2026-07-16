package com.pressureagent.mobile.ui.chat

import com.pressureagent.mobile.domain.model.Action
import com.pressureagent.mobile.domain.model.Confirmation
import com.pressureagent.mobile.domain.model.Risk
import com.pressureagent.mobile.domain.model.ServiceOrder
import com.pressureagent.mobile.domain.model.Task

/**
 * 对话气泡数据。text 为空时只渲染 richCard。
 */
data class ChatItem(
    val id: String,
    val text: String = "",
    val isUser: Boolean,
    val timestamp: String = "",
    val richCard: RichCard? = null,
)

/**
 * 嵌入对话流的富媒体卡片类型。
 * 消息、方案、风险、任务、确认全部在聊天气泡中呈现。
 */
sealed class RichCard {
    data class RiskInfo(val risk: Risk, val conclusion: String, val eta: String?) : RichCard()
    data class TaskList(val tasks: List<Task>) : RichCard()
    data class ServicePlan(val order: ServiceOrder) : RichCard()
    data class MessageDraft(val action: Action) : RichCard()
    data class ConfirmRequest(val confirmationId: String, val prompt: String) : RichCard()
}
