package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ApiKeyScreen
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
                onNavigateToApiKey = { navController.navigate("api_key") }
            )
        }
        composable("api_key") {
            ApiKeyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
