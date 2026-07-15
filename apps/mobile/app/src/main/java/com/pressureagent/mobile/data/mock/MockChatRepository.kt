package com.pressureagent.mobile.data.mock

import com.pressureagent.mobile.data.local.CalendarHelper
import com.pressureagent.mobile.data.repository.ChatRepository
import com.pressureagent.mobile.data.repository.ChatStreamEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Mock implementation of [ChatRepository] for debug builds.
 *
 * Uses a fuzzy intent scoring system instead of exact keyword matching,
 * so natural language variations work without needing precise trigger phrases.
 *
 * The real backend would use an LLM for intent understanding;
 * this mock approximates it with synonym groups + confidence scoring.
 */
class MockChatRepository(
    private val calendarHelper: CalendarHelper? = null,
) : ChatRepository {

    private var sessionId: String = "mock_session_${UUID.randomUUID().toString().take(8)}"

    // ─── Intent scoring ────────────────────────────────────────────────────────

    /**
     * Each intent has a set of (pattern → weight) pairs.
     * Weight 5 = core meaning word, nearly certain.
     * Weight 3 = strong indicator.
     * Weight 1 = weak / contextual hint.
     */
    private data class IntentPattern(val patterns: List<Pair<String, Int>>, val priority: Int)

    private val intentPatterns = mapOf(
        "create_task" to IntentPattern(
            patterns = listOf(
                "创建" to 5, "新建" to 5, "添加" to 3, "加个" to 4,
                "帮我建" to 4, "帮我加" to 4, "帮我设" to 3,
                "任务" to 3, "事情" to 2, "要做" to 2, "待办" to 2,
                "安排" to 2, "记一下" to 2, "记录" to 1,
                "接孩子" to 4, "接小孩" to 4, "放学" to 4,
                "送孩子" to 4, "送小孩" to 4, "上学" to 4,
                "去超市" to 4, "买菜" to 4, "买东西" to 3,
                "去医院" to 4, "看病" to 3, "体检" to 3,
                "开会" to 4, "出差" to 3,
            ), priority = 1,
        ),
        "create_calendar_event" to IntentPattern(
            patterns = listOf(
                "日程" to 5, "日历" to 5, "提醒我" to 4, "到时间" to 3,
                "几点" to 2, "明天" to 2, "后天" to 2, "下周" to 2,
                "几号" to 2, "几时" to 2, "定时" to 3, "闹钟" to 3,
            ), priority = 0, // Higher priority than create_task when calendar keywords present
        ),
        "report_delay" to IntentPattern(
            patterns = listOf(
                "延迟" to 5, "晚了" to 5, "迟到" to 5, "来不及" to 5,
                "会议" to 3, "超时" to 4, "耽误" to 4, "拖延" to 4,
                "赶不上" to 5, "推迟" to 4, "延时" to 4, "拖了" to 4,
                "可能要晚" to 5, "会晚" to 4, "搞不完" to 3,
                "还没结束" to 3, "没开完" to 4, "走不了" to 3,
                "时间不够" to 3, "可能赶" to 3, "要迟" to 4,
            ), priority = 2,
        ),
        "report_vehicle" to IntentPattern(
            patterns = listOf(
                "出发" to 5, "出发了" to 5, "走啦" to 5, "走了" to 4,
                "开车" to 5, "上车" to 5, "出门" to 4, "出门了" to 4,
                "在路上了" to 5, "已经出发" to 5, "动身" to 4,
                "启程" to 3, "赶路" to 3,
            ), priority = 2,
        ),
        "report_traffic" to IntentPattern(
            patterns = listOf(
                "堵车" to 5, "堵" to 4, "拥堵" to 5, "塞车" to 5, "塞" to 3,
                "路况" to 3, "交通" to 2,
                "走走停停" to 5, "排长队" to 4, "动不了" to 4,
                "好慢" to 3, "怎么这么慢" to 3, "太慢了" to 3,
                "前面堵" to 5, "路上堵" to 5, "高速堵" to 5,
                "不通" to 3, "慢死了" to 4, "快不了" to 3,
                "车多" to 3, "车太多了" to 4, "一动不动" to 5,
            ), priority = 3,
        ),
        "request_assistance" to IntentPattern(
            patterns = listOf(
                "帮忙" to 4, "帮帮我" to 5, "帮我" to 3, "救我" to 4,
                "接管" to 5, "接手" to 5, "处理" to 2,
                "压力" to 4, "压力大" to 5, "受不了" to 4,
                "怎么办" to 3, "快疯了" to 5, "焦虑" to 4,
                "应付不了" to 4, "搞不定" to 4, "撑不住" to 5,
                "拜托" to 3, "靠你了" to 5, "交给你" to 5,
                "想想办法" to 4, "有没有办法" to 3,
            ), priority = 4,
        ),
        "draft_message" to IntentPattern(
            patterns = listOf(
                "通知" to 4, "发消息" to 5, "发信息" to 5, "告诉" to 3,
                "发送" to 3, "消息" to 2, "短信" to 3,
                "老师" to 4, "王老师" to 5, "家人" to 4,
                "说一下" to 3, "打个招呼" to 3, "联系" to 2,
                "转告" to 4, "传达" to 3, "知会" to 3,
            ), priority = 5,
        ),
        "get_status" to IntentPattern(
            patterns = listOf(
                "状态" to 4, "情况" to 3, "怎么样" to 2,
                "看看" to 2, "查" to 2, "检查" to 2,
                "现在什么" to 4, "当前" to 2, "目前" to 2, "现在" to 1,
                "进展" to 3, "进度" to 3,
            ), priority = 2,
        ),
        "help" to IntentPattern(
            patterns = listOf(
                "帮助" to 5, "能做什么" to 5, "能干嘛" to 5,
                "功能" to 5, "会什么" to 5, "你会" to 4, "你能" to 3,
                "有什么" to 3, "介绍一下" to 4, "怎么用" to 4,
            ), priority = 1,
        ),
    )

    /**
     * Score every intent against the user message.
     * Returns intents sorted by (score * priority) descending.
     * Only returns intents with score >= threshold.
     */
    private fun scoreIntents(message: String, threshold: Int = 3): List<ScoredIntent> {
        val lower = message.lowercase()
        val results = mutableListOf<ScoredIntent>()

        for ((intentName, pattern) in intentPatterns) {
            var totalScore = 0
            for ((keyword, weight) in pattern.patterns) {
                if (lower.contains(keyword.lowercase())) {
                    totalScore += weight
                }
            }
            if (totalScore >= threshold) {
                results.add(ScoredIntent(intentName, totalScore, pattern.priority))
            }
        }

        // Sort: higher score first; ties broken by higher priority
        return results.sortedWith(compareByDescending<ScoredIntent> { it.score }
            .thenByDescending { it.priority })
    }

    private data class ScoredIntent(
        val name: String,
        val score: Int,
        val priority: Int,
    )

    // ─── Main flow ─────────────────────────────────────────────────────────────

    override fun sendMessage(
        message: String,
        inputMode: String,
        sessionId: String?,
    ): Flow<ChatStreamEvent> = flow {
        if (sessionId != null) this@MockChatRepository.sessionId = sessionId

        val lower = message.lowercase()
        val intents = scoreIntents(message)

        when {
            // ─── No clear intent → try to guess, then fall back ────────────
            intents.isEmpty() -> handleAmbiguous(lower)

            // ─── Multi-intent: process them in priority order ───────────────
            else -> {
                var first = true
                // Process top intents (up to 3), highest scoring first
                val topIntents = intents.take(3)
                for (scored in topIntents) {
                    if (!first) {
                        emitTextWithDelay("同时，")
                    }
                    first = false
                    when (scored.name) {
                        "help" -> showFullHelp()
                        "create_calendar_event" -> handleCalendarEvent(message)
                        "create_task" -> handleCreateTask(message)
                        "report_delay" -> handleReportDelay(message)
                        "report_vehicle" -> handleReportVehicle()
                        "report_traffic" -> handleReportTraffic()
                        "draft_message" -> handleDraftMessage()
                        "request_assistance" -> handleRequestAssistance()
                        "get_status" -> handleGetStatus()
                    }
                }
                // Closing remark
                if (topIntents.size > 1) {
                    delay(200)
                    emitTextWithDelay("还有其他需要吗？")
                }
            }
        }

        emit(ChatStreamEvent.Done(this@MockChatRepository.sessionId, revision = 1))
    }

    // ─── Intent handlers ───────────────────────────────────────────────────────

    private suspend fun FlowCollector<ChatStreamEvent>.handleCalendarEvent(message: String) {
        val title = extractTitle(message)
        val toolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emitTextWithDelay("好的，我来添加日程。")
        emit(ChatStreamEvent.ToolCallStarted(toolId, "create_calendar_event",
            """{"title":"$title"}"""))
        delay(400)
        val eventId = calendarHelper?.createEvent(title = title)
        if (eventId != null) {
            emit(ChatStreamEvent.ToolCallResult(toolId, true, "已添加到系统日历：$title"))
            delay(200)
            emitTextWithDelay("「$title」已经加到日历了，到时间会提醒你。")
        } else {
            emit(ChatStreamEvent.ToolCallResult(toolId, false, "无法访问日历，请授权"))
            delay(200)
            emitTextWithDelay("需要日历权限才能同步，在系统设置里允许一下就好。")
        }
    }

    private suspend fun FlowCollector<ChatStreamEvent>.handleCreateTask(message: String) {
        val title = extractTitle(message)
        val toolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emitTextWithDelay("收到，创建任务「$title」。")
        emit(ChatStreamEvent.ToolCallStarted(toolId, "create_task",
            """{"title":"$title","type":"rigid","priority":"high"}"""))
        delay(400)
        emit(ChatStreamEvent.ToolCallResult(toolId, true, "已创建任务：$title"))
        delay(200)
        emitTextWithDelay("任务已建好。需要同步到日历吗？")
    }

    private suspend fun FlowCollector<ChatStreamEvent>.handleReportDelay(message: String) {
        emitTextWithDelay("会议延迟了，我来重新评估时间。")
        val toolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emit(ChatStreamEvent.ToolCallStarted(toolId, "report_meeting_delay", """{}"""))
        delay(400)
        emit(ChatStreamEvent.ToolCallResult(toolId, true, "已记录延迟，正在评估对后续任务的影响。"))
        delay(200)
        emitTextWithDelay("接下来有刚性任务需要关注——需要我帮你看看怎么办吗？")
    }

    private suspend fun FlowCollector<ChatStreamEvent>.handleReportVehicle() {
        emitTextWithDelay("好的，已记录出发。")
        val toolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emit(ChatStreamEvent.ToolCallStarted(toolId, "report_vehicle_mode", """{"mode":"driving"}"""))
        delay(300)
        emit(ChatStreamEvent.ToolCallResult(toolId, true, "已切换到驾驶模式。"))
        delay(200)
        emitTextWithDelay("驾驶注意安全，需要帮你留意路况。")
    }

    private suspend fun FlowCollector<ChatStreamEvent>.handleReportTraffic() {
        emitTextWithDelay("路上在堵，我来更新路况。")
        val toolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emit(ChatStreamEvent.ToolCallStarted(toolId, "report_traffic", """{"congestion":true,"delayMinutes":18}"""))
        delay(400)
        emit(ChatStreamEvent.ToolCallResult(toolId, true, "路况已更新，预计延误18分钟。"))
        delay(200)
        emitTextWithDelay("照这个速度要晚不少。要不要帮你通知一下老师和家人？")
    }

    private suspend fun FlowCollector<ChatStreamEvent>.handleDraftMessage() {
        emitTextWithDelay("好的，准备消息。")
        val teacherToolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emit(ChatStreamEvent.ToolCallStarted(teacherToolId, "draft_message",
            """{"audience":"teacher","body":"王老师您好，路上拥堵，预计会晚到一会，麻烦稍等一下，谢谢。"}"""))
        delay(500)
        emit(ChatStreamEvent.ToolCallResult(teacherToolId, true, "已生成给老师的消息"))

        val familyToolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emit(ChatStreamEvent.ToolCallStarted(familyToolId, "draft_message",
            """{"audience":"family","body":"路上有点堵，可能晚一些到学校，超市改到接完孩子再去。"}"""))
        delay(500)
        emit(ChatStreamEvent.ToolCallResult(familyToolId, true, "已生成给家人的消息"))
        delay(200)

        emit(ChatStreamEvent.ConfirmationRequired(
            confirmationId = "confirm_${UUID.randomUUID().toString().take(8)}",
            prompt = "消息已准备好，要发送吗？",
            actionIds = listOf("action_notify_teacher", "action_notify_family"),
        ))
    }

    private suspend fun FlowCollector<ChatStreamEvent>.handleRequestAssistance() {
        emitTextWithDelay("收到，我来接手。")
        delay(200)
        val toolId = "tc_${UUID.randomUUID().toString().take(8)}"
        emit(ChatStreamEvent.ToolCallStarted(toolId, "request_assistance",
            """{"reason":"用户请求接管"}"""))
        delay(600)
        emit(ChatStreamEvent.ToolCallResult(toolId, true, "Agent 已接管"))
        delay(200)
        emitTextWithDelay("我已经在处理了。刚性任务（接孩子）优先级最高，弹性任务可以往后排。接下来要帮你通知老师和家人，要我继续吗？")
    }

    private suspend fun FlowCollector<ChatStreamEvent>.handleGetStatus() {
        emitTextWithDelay("当前状态：\n\n• 任务：接孩子（18:10 阳光小学）、超市采购（19:30）\n• 车辆：未启动\n• 风险：无\n• 腕上设备：已连接\n\n一切正常。有什么需要随时说。")
    }

    // ─── Ambiguous / fallback ──────────────────────────────────────────────────

    private suspend fun FlowCollector<ChatStreamEvent>.handleAmbiguous(lower: String) {
        // Check for casual conversation
        if (lower.contains("你好") || lower.contains("嗨") || lower.contains("嘿") || lower.contains("在吗")) {
            emitTextWithDelay("你好！有什么可以帮你的？直接说就行，不用想命令。")
            return
        }
        if (lower.contains("谢谢") || lower.contains("感谢") || lower.contains("多谢")) {
            val replies = listOf("不客气！", "应该的。", "随时找我。", "没问题，还有什么需要吗？")
            emitTextWithDelay(replies.random())
            return
        }
        if (lower.contains("再见") || lower.contains("拜拜") || lower.contains("回头聊")) {
            emitTextWithDelay("再见！注意安全。")
            return
        }
        if (lower.contains("你叫什么") || lower.contains("你是谁")) {
            emitTextWithDelay("我是随行压力接管助手，专门帮你在时间紧张、压力大的情况下协调任务和通知。直接跟我说你的情况就好。")
            return
        }

        // Partial match: give helpful hints based on what we detected
        val hints = mutableListOf<String>()

        // Check for task-related but too vague
        if (lower.contains("孩子") || lower.contains("小孩")) hints.add("孩子")
        if (lower.contains("接")) hints.add("接")
        if (lower.contains("送")) hints.add("送")
        if (lower.contains("学校") || lower.contains("幼儿园")) hints.add("学校")
        if (lower.contains("路") || lower.contains("车")) hints.add("路况/车辆")
        if (lower.contains("晚") || lower.contains("慢") || lower.contains("急")) hints.add("时间/延迟")
        if (lower.contains("消息") || lower.contains("说") || lower.contains("讲")) hints.add("通知/消息")

        if (hints.isNotEmpty()) {
            val hintStr = hints.joinToString("、")
            emitTextWithDelay("我注意到你提到了$hintStr，但我没完全理解具体需求。能多说一点吗？比如：\n• 「我在接孩子的路上堵车了」\n• 「会议晚了，担心来不及」\n• 「帮我通知老师会迟到」")
        } else {
            emitTextWithDelay("我没太理解你的意思 😅 你可以说得更具体一点，比如：\n• 「路上的情况不太好，可能要迟到」\n• 「帮我安排接孩子的事」\n• 「现在什么情况」\n\n不用记命令，怎么自然怎么说。")
        }
    }

    // ─── Help ──────────────────────────────────────────────────────────────────

    private suspend fun FlowCollector<ChatStreamEvent>.showFullHelp() {
        emitTextWithDelay("我能帮你做这些事，不用记命令，怎么自然怎么说：\n\n")
        emitTextWithDelay("📝 **创建任务** — 「帮我加个接孩子的任务」「创建明天下午开会」\n\n")
        emitTextWithDelay("📅 **同步日历** — 「明天下午3点提醒我去超市」「加个日程」\n\n")
        emitTextWithDelay("⏰ **处理延迟** — 「会议搞不完了」「可能要晚了来不及了」\n\n")
        emitTextWithDelay("🚗 **驾驶模式** — 「我出发了」「出门了在路上」\n\n")
        emitTextWithDelay("🚦 **路况更新** — 「堵死了走不动」「前面怎么这么慢」\n\n")
        emitTextWithDelay("📨 **起草通知** — 「能帮我跟老师说一声吗」「通知一下家人」\n\n")
        emitTextWithDelay("🆘 **紧急接管** — 「快帮帮我」「压力太大了你替我处理」\n\n")
        emitTextWithDelay("---\n\n")
        emitTextWithDelay("一句话里有好几件事也没关系，我会一起处理。")
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun FlowCollector<ChatStreamEvent>.emitTextWithDelay(text: String) {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val chunkSize = minOf(remaining.length, 3.coerceAtLeast(1))
            emit(ChatStreamEvent.TextDelta(remaining.take(chunkSize)))
            remaining = remaining.drop(chunkSize)
            delay(30L)
        }
    }

    private fun extractTitle(message: String): String {
        // Try common patterns first
        val patterns = listOf(
            Regex("接.{0,3}(孩子|小孩|放学|娃)"),
            Regex("送.{0,3}(孩子|小孩|上学|娃)"),
            Regex("去.{0,5}(超市|买菜|买东西|购物|医院|学校|幼儿园)"),
            Regex("开.{0,2}会"),
            Regex("买.{0,3}(菜|东西|水果|药)"),
            Regex("做.{0,3}(饭|菜|晚饭|午饭|早餐)"),
            Regex("明天.{0,6}(下午|上午|晚上).{0,4}"),
            Regex("(下午|上午|晚上).{0,2}(点|时).{0,6}"),
        )
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) return match.value.trim()
        }
        return message.take(20).trim().ifEmpty { "新建任务" }
    }

    // ─── Confirm action ────────────────────────────────────────────────────────

    override suspend fun confirmAction(
        sessionId: String,
        confirmationId: String,
        decision: String,
    ) {
        // Mock: no-op
    }
}
