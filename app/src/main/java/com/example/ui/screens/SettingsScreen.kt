package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToApiKey: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = uiState.isDarkTheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = if (isDark) ZarpTextPrimary else LightTextPrimary, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) ZarpTextPrimary else LightTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) ZarpMainBg else LightMainBg
                )
            )
        },
        containerColor = if (isDark) ZarpMainBg else LightMainBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionTitle("Appearance", isDark)
            SettingsItemToggle(
                title = "Dark Mode",
                initialValue = isDark,
                onToggle = { viewModel.onToggleTheme() },
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle("General", isDark)
            SettingsItemValue("Language", "English", isDark)

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle("Data & Privacy", isDark)
            SettingsItemToggle("Chat history", true, {}, isDark)
            SettingsItemAction("API Key", isDark) { onNavigateToApiKey() }
            SettingsItemAction("Export data", isDark)
            SettingsItemAction("Delete account", isDark, Color.Red)

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle("About", isDark)
            SettingsItemValue("Version", "1.0.0", isDark)
            SettingsItemAction("Terms of use", isDark)
            SettingsItemAction("Privacy policy", isDark)
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String, isDark: Boolean) {
    Text(
        text = title,
        color = if (isDark) ZarpTextTertiary else LightTextTertiary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItemValue(title: String, value: String, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isDark) ZarpTextPrimary else LightTextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = if (isDark) ZarpTextTertiary else LightTextTertiary,
            fontSize = 16.sp
        )
    }
}

@Composable
fun SettingsItemToggle(
    title: String,
    initialValue: Boolean,
    onToggle: (Boolean) -> Unit = {},
    isDark: Boolean
) {
    var checked by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                onToggle(checked)
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isDark) ZarpTextPrimary else LightTextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                onToggle(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (isDark) ZarpAccent else LightAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = if (isDark) ZarpDivider else LightDivider
            )
        )
    }
}

@Composable
fun SettingsItemAction(
    title: String,
    isDark: Boolean,
    textColor: Color = if (isDark) ZarpTextPrimary else LightTextPrimary,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = textColor, fontSize = 16.sp)
    }
}
