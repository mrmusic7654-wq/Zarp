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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpMainBg
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings", color = ZarpTextPrimary, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ZarpTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ZarpMainBg
                )
            )
        },
        containerColor = ZarpMainBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionTitle("General")
            SettingsItemValue("Theme", "System")
            SettingsItemToggle("Haptic feedback", true)
            SettingsItemValue("Language", "English")

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle("Data & Privacy")
            SettingsItemToggle("Chat history", true)
            SettingsItemAction("Export data")
            SettingsItemAction("Delete account", Color.Red)

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle("About")
            SettingsItemValue("Version", "1.0.0")
            SettingsItemAction("Terms of use")
            SettingsItemAction("Privacy policy")
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = ZarpTextTertiary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItemValue(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = ZarpTextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = ZarpTextTertiary, fontSize = 16.sp)
    }
}

@Composable
fun SettingsItemToggle(title: String, initialValue: Boolean) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = ZarpTextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZarpMainBg,
                checkedTrackColor = ZarpAccent
            )
        )
    }
}

@Composable
fun SettingsItemAction(title: String, textColor: Color = ZarpTextPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}
