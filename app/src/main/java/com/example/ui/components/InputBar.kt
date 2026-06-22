package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*

@Composable
fun InputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
    onAttachmentTap: () -> Unit,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    isThinking: Boolean = false,
    isPaused: Boolean = false,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    attachedImageUris: List<Uri> = emptyList(),
    attachedFileNames: List<String> = emptyList(),
    attachedFileTypes: List<String> = emptyList(),
    onRemoveAttachment: (Int) -> Unit = {},
    isTranslateMode: Boolean = false,
    onToggleTranslateMode: () -> Unit = {},
    isSearchMode: Boolean = false,
    onToggleSearchMode: () -> Unit = {},
    isAgentMode: Boolean = false,
    onToggleAgentMode: () -> Unit = {},
    isAutomationMode: Boolean = false,
    onToggleAutomationMode: () -> Unit = {},
    isVoiceMode: Boolean = false,
    onToggleVoiceMode: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 16.dp)
    ) {
        // ── Attachment chips ──
        if (attachedImageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZarpInputBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachedImageUris.forEachIndexed { index, uri ->
                    val fileType = attachedFileTypes.getOrElse(index) { "📎" }
                    val fileName = attachedFileNames.getOrElse(index) { "File" }
                    Box {
                        Row(
                            modifier = Modifier
                                .background(ZarpBubbleBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fileType, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (fileName.length > 15) fileName.take(15) + "..." else fileName,
                                color = ZarpTextPrimary, fontSize = 11.sp, maxLines = 1
                            )
                            IconButton(onClick = { onRemoveAttachment(index) }, modifier = Modifier.size(14.dp)) {
                                Icon(Icons.Default.Close, "Remove", tint = ZarpTextTertiary, modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Input row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    ZarpInputBg,
                    if (attachedImageUris.isNotEmpty()) RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
                    else RoundedCornerShape(26.dp)
                )
                .border(1.dp, ZarpInputBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onAttachmentTap() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AttachFile, "Attach", tint = ZarpTextTertiary, modifier = Modifier.size(18.dp))
            }

            // Search
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onToggleSearchMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.TravelExplore, "Search", tint = if (isSearchMode) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(16.dp))
            }

            // Translate
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onToggleTranslateMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Translate, "Translate", tint = if (isTranslateMode) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(16.dp))
            }

            // Agent
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onToggleAgentMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, "Agent", tint = if (isAgentMode) Color(0xFF00E676) else ZarpTextTertiary, modifier = Modifier.size(16.dp))
            }

            // Automation
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onToggleAutomationMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SettingsSuggest, "Automation", tint = if (isAutomationMode) Color(0xFFCE93D8) else ZarpTextTertiary, modifier = Modifier.size(16.dp))
            }

            // Voice mode
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onToggleVoiceMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.KeyboardVoice, "Voice", tint = if (isVoiceMode) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Text field
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                val placeholder = when {
                    isListening -> "Listening..."
                    isAutomationMode -> "Automation: describe task..."
                    isAgentMode -> "Agent: describe your app..."
                    isSearchMode && isTranslateMode -> "Search + Translate..."
                    isSearchMode -> "Search the web..."
                    isTranslateMode -> "Type in any language..."
                    else -> "Message Zarp..."
                }
                if (inputText.isEmpty()) Text(placeholder, color = ZarpTextTertiary, fontSize = 13.sp)
                if (!isListening) {
                    BasicTextField(
                        value = inputText, onValueChange = onInputChanged,
                        textStyle = TextStyle(color = ZarpTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(ZarpAccent), maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Action button
            Crossfade(targetState = when { isThinking -> "pause"; isPaused -> "resume"; inputText.isNotBlank() || attachedImageUris.isNotEmpty() -> "send"; else -> "mic" }, animationSpec = tween(200), label = "action") { state ->
                when (state) {
                    "pause" -> Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFFFC107)).clickable { onPause() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Pause, "Pause", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    "resume" -> Box(Modifier.size(34.dp).clip(CircleShape).background(ZarpAccent).clickable { onResume() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, "Resume", tint = ZarpMainBg, modifier = Modifier.size(18.dp))
                    }
                    "send" -> Box(Modifier.size(34.dp).clip(CircleShape).background(
                        when { isAutomationMode -> Color(0xFFCE93D8); isAgentMode -> Color(0xFF00E676); else -> ZarpAccent }
                    ).clickable { onSend() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowUpward, "Send", tint = ZarpMainBg, modifier = Modifier.size(16.dp))
                    }
                    else -> Box(Modifier.size(32.dp).clip(CircleShape).clickable { onMicTap() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, "Mic", tint = ZarpTextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ── Mode indicators ──
        if (isAgentMode || isAutomationMode || isVoiceMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isAgentMode) ModeChip("🤖 Agent", Color(0xFF00E676))
                if (isAutomationMode) ModeChip("🎮 Auto", Color(0xFFCE93D8))
                if (isVoiceMode) ModeChip("🎤 Voice", ZarpAccent)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
