package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ZarpAccent,
    secondary = ZarpTextSecondary,
    tertiary = ZarpTextTertiary,
    background = ZarpMainBg,
    surface = ZarpSidebarBg,
    onPrimary = Color(0xFF1A1A1A),
    onSecondary = ZarpTextPrimary,
    onTertiary = ZarpTextPrimary,
    onBackground = ZarpTextPrimary,
    onSurface = ZarpTextPrimary,
    surfaceVariant = ZarpBubbleBg,
    outline = ZarpInputBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    secondary = Color(0xFF5F6368),
    tertiary = Color(0xFF80868B),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color(0xFF202124),
    onTertiary = Color(0xFF202124),
    onBackground = Color(0xFF202124),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF1F3F4),
    outline = Color(0xFFDADCE0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
