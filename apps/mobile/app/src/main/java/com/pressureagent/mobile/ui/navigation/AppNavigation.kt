package com.pressureagent.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pressureagent.mobile.ui.debug.DebugScreen
import com.pressureagent.mobile.ui.home.HomeScreen
import com.pressureagent.mobile.ui.message.MessageListScreen
import com.pressureagent.mobile.ui.splash.SplashScreen
import com.pressureagent.mobile.ui.task.CreateTaskScreen
import com.pressureagent.mobile.ui.task.TaskListScreen
import com.pressureagent.mobile.ui.voice.VoiceChatScreen
import com.pressureagent.mobile.ui.wearable.WearableScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val tabs = listOf(Screen.Home, Screen.Tasks, Screen.Messages, Screen.Wearable, Screen.Debug)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 仅在非开屏页显示底部栏
    val showBottomBar = currentRoute != Screen.Splash.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    tabs.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(onNavigateToVoiceChat = { navController.navigate(Screen.VoiceChat.route) })
            }
            composable(Screen.Tasks.route) {
                TaskListScreen(onNavigateToCreate = { navController.navigate(Screen.TaskCreate.route) })
            }
            composable(Screen.Messages.route) { MessageListScreen() }
            composable(Screen.Wearable.route) { WearableScreen() }
            composable(Screen.Debug.route) { DebugScreen() }
            composable(Screen.TaskCreate.route) {
                CreateTaskScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.VoiceChat.route) {
                VoiceChatScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
