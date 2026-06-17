package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ZarpAccent,
    secondary = ZarpTextSecondary,
    tertiary = ZarpTextTertiary,
    background = ZarpMainBg,
    surface = ZarpSidebarBg,
    onPrimary = ZarpMainBg,
    onSecondary = ZarpTextPrimary,
    onTertiary = ZarpTextPrimary,
    onBackground = ZarpTextPrimary,
    onSurface = ZarpTextPrimary,
)

private val LightColorScheme = DarkColorScheme // Forced dark theme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
