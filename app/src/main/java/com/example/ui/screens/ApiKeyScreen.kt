package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KeyManager
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(KeyManager.getApiKey(context) ?: "") }
    var showSavedMessage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Key", color = ZarpTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ZarpTextPrimary
                        )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Enter your Gemini API Key",
                color = ZarpTextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                placeholder = { Text("Paste your API key here", color = ZarpTextTertiary) },
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
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (key.isBlank()) {
                        Toast.makeText(context, "Key cannot be empty", Toast.LENGTH_SHORT).show()
                    } else {
                        KeyManager.saveApiKey(context, key.trim())
                        showSavedMessage = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Key", color = ZarpMainBg)
            }
            if (showSavedMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Key saved securely!", color = ZarpAccent, fontSize = 14.sp)
            }
        }
    }
}
