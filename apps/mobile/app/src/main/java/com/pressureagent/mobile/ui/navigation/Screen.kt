package com.pressureagent.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    // ─── 主 Tab（底部导航栏 4 个）─────────────────────────────────────────
    data object Chat : Screen("chat", "对话", Icons.AutoMirrored.Filled.Chat)
    data object Calendar : Screen("calendar", "日程", Icons.Filled.CalendarMonth)
    data object Vehicle : Screen("vehicle", "车辆", Icons.Filled.DirectionsCar)
    data object Profile : Screen("profile", "我的", Icons.Filled.Person)

    // ─── 二级页面（不在底部导航栏）────────────────────────────────────────
    data object Splash : Screen("splash", "开屏", Icons.Filled.Home)
    data object TaskCreate : Screen("task_create", "创建任务", Icons.Filled.Add)
    data object Review : Screen("review", "复盘", Icons.Filled.CalendarMonth)
    data object Debug : Screen("debug", "调试", Icons.Filled.Settings)
    data object LogViewer : Screen("log_viewer", "日志", Icons.Filled.Settings)
}
