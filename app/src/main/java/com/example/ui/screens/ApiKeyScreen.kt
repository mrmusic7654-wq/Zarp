package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KeyManager
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    var geminiKey by remember { mutableStateOf(KeyManager.getGeminiKey(context) ?: "") }
    var githubKey by remember { mutableStateOf(KeyManager.getGithubKey(context) ?: "") }
    var telegramKey by remember { mutableStateOf(KeyManager.getTelegramKey(context) ?: "") }
    var openaiKey by remember { mutableStateOf(KeyManager.getOpenAIKey(context) ?: "") }
    var huggingFaceKey by remember { mutableStateOf(KeyManager.getHuggingFaceKey(context) ?: "") }
    var hfSpaceUrl by remember { mutableStateOf(KeyManager.getHFSpaceUrl(context)) }

    val customKeys = remember {
        mutableStateListOf<String>().apply {
            repeat(10) { add(KeyManager.getCustomKey(context, it) ?: "") }
        }
    }

    var showSavedBanner by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔑", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("API Keys", color = ZarpTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ZarpTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZarpMainBg)
            )
        },
        containerColor = ZarpMainBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Banner
            if (showSavedBanner) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = ZarpAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keys stored encrypted on this device only", color = ZarpAccent, fontSize = 12.sp)
                }
            }

            // ── Premium Services ──
            SectionHeader("🔮 AI & Search")

            KeyField(
                icon = "🔮",
                label = "Gemini API Key",
                subtitle = "Required for AI chat",
                value = geminiKey,
                placeholder = "AIzaSy...",
                onValueChange = { geminiKey = it },
                onSave = {
                    KeyManager.saveGeminiKey(context, geminiKey.trim())
                    showSavedBanner = true
                }
            )

            KeyField(
                icon = "🚀",
                label = "HF Space URL",
                subtitle = "Your Zarp Search Engine endpoint",
                value = hfSpaceUrl,
                placeholder = "https://yourname-zarp-search.hf.space/search",
                onValueChange = { hfSpaceUrl = it },
                onSave = {
                    KeyManager.saveHFSpaceUrl(context, hfSpaceUrl.trim())
                    showSavedBanner = true
                },
                isUrl = true
            )

            HorizontalDivider(color = ZarpDivider, modifier = Modifier.padding(vertical = 8.dp))

            // ── Developer Tools ──
            SectionHeader("🛠️ Developer Tools")

            KeyField(
                icon = "🐙",
                label = "GitHub Token",
                subtitle = "Repo access, PRs, issues",
                value = githubKey,
                placeholder = "ghp_...",
                onValueChange = { githubKey = it },
                onSave = {
                    KeyManager.saveGithubKey(context, githubKey.trim())
                    showSavedBanner = true
                }
            )

            KeyField(
                icon = "🤗",
                label = "Hugging Face Token",
                subtitle = "Model hub access",
                value = huggingFaceKey,
                placeholder = "hf_...",
                onValueChange = { huggingFaceKey = it },
                onSave = {
                    KeyManager.saveHuggingFaceKey(context, huggingFaceKey.trim())
                    showSavedBanner = true
                }
            )

            HorizontalDivider(color = ZarpDivider, modifier = Modifier.padding(vertical = 8.dp))

            // ── Communication ──
            SectionHeader("📡 Communication")

            KeyField(
                icon = "✈️",
                label = "Telegram Bot Token",
                subtitle = "From @BotFather",
                value = telegramKey,
                placeholder = "123456:ABC-DEF...",
                onValueChange = { telegramKey = it },
                onSave = {
                    KeyManager.saveTelegramKey(context, telegramKey.trim())
                    showSavedBanner = true
                }
            )

            KeyField(
                icon = "🧠",
                label = "OpenAI API Key",
                subtitle = "GPT model access",
                value = openaiKey,
                placeholder = "sk-...",
                onValueChange = { openaiKey = it },
                onSave = {
                    KeyManager.saveOpenAIKey(context, openaiKey.trim())
                    showSavedBanner = true
                }
            )

            HorizontalDivider(color = ZarpDivider, modifier = Modifier.padding(vertical = 8.dp))

            // ── Custom Slots ──
            SectionHeader("🔧 Custom Slots (${customKeys.size})")

            for (i in 0 until 10) {
                val label = when (i) {
                    0 -> "Custom Slot 1"
                    1 -> "Custom Slot 2"
                    else -> "Custom Slot ${i + 1}"
                }
                KeyField(
                    icon = "🔧",
                    label = label,
                    subtitle = "Any service",
                    value = customKeys[i],
                    placeholder = "Enter any API key...",
                    onValueChange = { customKeys[i] = it },
                    onSave = {
                        KeyManager.saveCustomKey(context, i, customKeys[i].trim())
                        showSavedBanner = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = ZarpTextTertiary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun KeyField(
    icon: String,
    label: String,
    subtitle: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    isUrl: Boolean = false
) {
    val context = LocalContext.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = ZarpTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(subtitle, color = ZarpTextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 24.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(placeholder, color = ZarpTextTertiary, fontSize = 12.sp)
                },
                visualTransformation = if (isUrl) PasswordVisualTransformation() else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = if (isUrl) KeyboardType.Uri else KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ZarpTextPrimary,
                    unfocusedTextColor = ZarpTextPrimary,
                    focusedBorderColor = ZarpAccent,
                    unfocusedBorderColor = ZarpInputBorder,
                    cursorColor = ZarpAccent
                ),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (value.isBlank()) {
                        Toast.makeText(context, "Cannot be empty", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave()
                        Toast.makeText(context, "✅ Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Save", color = ZarpMainBg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
