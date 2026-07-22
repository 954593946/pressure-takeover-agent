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
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pressureagent.mobile.ui.calendar.CalendarScreen
import com.pressureagent.mobile.ui.chat.ChatScreen
import com.pressureagent.mobile.ui.debug.DebugScreen
import com.pressureagent.mobile.ui.log.LogViewerScreen
import com.pressureagent.mobile.ui.profile.ProfileScreen
import com.pressureagent.mobile.ui.review.ReviewScreen
import com.pressureagent.mobile.ui.splash.SplashScreen
import com.pressureagent.mobile.ui.task.CreateTaskScreen
import com.pressureagent.mobile.ui.vehicle.VehicleScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val tabs = listOf(Screen.Chat, Screen.Calendar, Screen.Vehicle, Screen.Profile)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Splash.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                ) {
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
            // ─── Splash ────────────────────────────────────────────────────
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                )
            }

            // ─── 4 个主 Tab ────────────────────────────────────────────────
            composable(Screen.Chat.route) {
                ChatScreen()
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onNavigateToCreate = { navController.navigate(Screen.TaskCreate.route) },
                )
            }
            composable(Screen.Vehicle.route) {
                VehicleScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToReview = { navController.navigate(Screen.Review.route) },
                    onNavigateToDebug = { navController.navigate(Screen.Debug.route) },
                    onNavigateToLogViewer = { navController.navigate(Screen.LogViewer.route) },
                )
            }

            // ─── 二级页面 ──────────────────────────────────────────────────
            composable(Screen.TaskCreate.route) {
                CreateTaskScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Review.route) {
                ReviewScreen()
            }
            composable(Screen.Debug.route) {
                DebugScreen()
            }
            composable(Screen.LogViewer.route) {
                LogViewerScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
