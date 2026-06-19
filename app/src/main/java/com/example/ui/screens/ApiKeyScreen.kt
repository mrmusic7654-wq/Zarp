package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    // 10 custom slots
    val customKeys = remember { mutableStateListOf<String>().apply { repeat(10) { add(KeyManager.getCustomKey(context, it) ?: "") } } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Keys", color = ZarpTextPrimary, fontSize = 20.sp) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("All keys are stored encrypted on this device only.", color = ZarpTextTertiary, fontSize = 13.sp)

            KeySection("🔮 Gemini API", "AI chat responses", geminiKey, "AIzaSy...", { geminiKey = it }, { KeyManager.saveGeminiKey(context, geminiKey.trim()) })
            KeySection("🐙 GitHub Token", "Repo access, gists", githubKey, "ghp_...", { githubKey = it }, { KeyManager.saveGithubKey(context, githubKey.trim()) })
            KeySection("✈️ Telegram Bot", "Bot token from @BotFather", telegramKey, "123456:ABC-DEF...", { telegramKey = it }, { KeyManager.saveTelegramKey(context, telegramKey.trim()) })
            KeySection("🧠 OpenAI API", "GPT models", openaiKey, "sk-...", { openaiKey = it }, { KeyManager.saveOpenAIKey(context, openaiKey.trim()) })
            KeySection("🤗 Hugging Face", "HF API token", huggingFaceKey, "hf_...", { huggingFaceKey = it }, { KeyManager.saveHuggingFaceKey(context, huggingFaceKey.trim()) })

            HorizontalDivider(color = ZarpDivider)

            Text("Custom API Slots", color = ZarpTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            for (i in 0 until 10) {
                KeySection(
                    title = "🔧 Custom Slot ${i + 1}",
                    subtitle = "Any service",
                    value = customKeys[i],
                    placeholder = "Enter key...",
                    onValueChange = { customKeys[i] = it },
                    onSave = { KeyManager.saveCustomKey(context, i, customKeys[i].trim()) }
                )
            }
        }
    }
}

@Composable
fun KeySection(
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    Column {
        Text(title, color = ZarpTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = ZarpTextTertiary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = ZarpTextTertiary, fontSize = 13.sp) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    if (value.isBlank()) Toast.makeText(context, "Key empty", Toast.LENGTH_SHORT).show()
                    else { onSave(); Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show() }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent),
                modifier = Modifier.height(50.dp)
            ) {
                Text("Save", color = ZarpMainBg)
            }
        }
    }
}
