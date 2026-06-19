package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ApiKeysScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "chat",
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable("chat") {
            ChatScreen(
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToApiKeys = { navController.navigate("api_keys") }
            )
        }
        composable("api_keys") {
            ApiKeysScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
