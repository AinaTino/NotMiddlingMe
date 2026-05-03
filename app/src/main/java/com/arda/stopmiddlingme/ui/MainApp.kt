package com.arda.stopmiddlingme.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arda.stopmiddlingme.ui.screen.dashboard.DashboardScreen
import com.arda.stopmiddlingme.ui.screen.history.HistoryScreen
import com.arda.stopmiddlingme.ui.screen.scanner.ScannerScreen
import com.arda.stopmiddlingme.ui.screen.settings.SettingsScreen

import androidx.compose.ui.tooling.preview.Preview
import com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Scanner : Screen("scanner", "Scanner", Icons.Default.Search)
    object History : Screen("history", "History", Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Dashboard,
        Screen.Scanner,
        Screen.History,
        Screen.Settings
    )

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
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
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(navController, startDestination = Screen.Dashboard.route, Modifier.padding(innerPadding)) {
                composable(Screen.Dashboard.route) { DashboardScreen() }
                composable(Screen.Scanner.route) { ScannerScreen() }
                composable(Screen.History.route) { HistoryScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppPreview() {
    StopMiddlingMeTheme {
        MainApp()
    }
}
