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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pressureagent.mobile.ui.debug.DebugScreen
import com.pressureagent.mobile.ui.home.HomeScreen
import com.pressureagent.mobile.ui.message.MessageListScreen
import com.pressureagent.mobile.ui.profile.ProfileScreen
import com.pressureagent.mobile.ui.review.ReviewScreen
import com.pressureagent.mobile.ui.service.ServicePlanScreen
import com.pressureagent.mobile.ui.splash.SplashScreen
import com.pressureagent.mobile.ui.task.CreateTaskScreen
import com.pressureagent.mobile.ui.task.TaskListScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val tabs = listOf(Screen.Home, Screen.Tasks, Screen.Messages, Screen.ServicePlan, Screen.Profile)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Splash.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.White,
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
                HomeScreen(
                    onNavigateToServicePlan = { navController.navigate(Screen.ServicePlan.route) },
                    onNavigateToReview = { navController.navigate(Screen.Review.route) },
                )
            }
            composable(Screen.Tasks.route) {
                TaskListScreen(onNavigateToCreate = { navController.navigate(Screen.TaskCreate.route) })
            }
            composable(Screen.Messages.route) { MessageListScreen() }
            composable(Screen.ServicePlan.route) { ServicePlanScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
            composable(Screen.Debug.route) { DebugScreen() }
            composable(Screen.TaskCreate.route) {
                CreateTaskScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Review.route) { ReviewScreen() }
        }
    }
}
