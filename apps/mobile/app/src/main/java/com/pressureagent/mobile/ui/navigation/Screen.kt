package com.pressureagent.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Watch
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Filled.Home)
    data object Tasks : Screen("tasks", "任务", Icons.Filled.TaskAlt)
    data object Messages : Screen("messages", "消息", Icons.Filled.MailOutline)
    data object VoiceChat : Screen("voice_chat", "AI 助手", Icons.Filled.Mic)
    data object Wearable : Screen("wearable", "腕上设备", Icons.Filled.Watch)
    data object Debug : Screen("debug", "调试", Icons.Filled.Settings)
    data object TaskCreate : Screen("task_create", "创建任务", Icons.Filled.Add)
    data object Splash : Screen("splash", "开屏", Icons.Filled.Home)
}
