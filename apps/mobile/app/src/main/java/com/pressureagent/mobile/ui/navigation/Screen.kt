package com.pressureagent.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Filled.Home)
    data object Tasks : Screen("tasks", "任务", Icons.Filled.TaskAlt)
    data object Messages : Screen("messages", "消息", Icons.Filled.MailOutline)
    data object ServicePlan : Screen("service_plan", "方案", Icons.Filled.ShoppingCart)
    data object Profile : Screen("profile", "我的", Icons.Filled.Person)
    data object Debug : Screen("debug", "调试", Icons.Filled.Settings)
    data object TaskCreate : Screen("task_create", "创建任务", Icons.AutoMirrored.Filled.Chat)
    data object Splash : Screen("splash", "开屏", Icons.Filled.Home)
    data object Review : Screen("review", "复盘", Icons.Filled.Receipt)
}
