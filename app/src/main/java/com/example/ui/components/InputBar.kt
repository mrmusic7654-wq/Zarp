package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val infiniteTransition = rememberInfiniteTransition(label = "input_anims")

    val micPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse"
    )

    val sendGlow by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "send_glow"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
    ) {
        if (attachedImageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZarpInputBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, ZarpInputBorder.copy(alpha = 0.3f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachedImageUris.forEachIndexed { index, uri ->
                    val fileType = attachedFileTypes.getOrElse(index) { "📎" }
                    val fileName = attachedFileNames.getOrElse(index) { "File" }
                    Box {
                        Row(
                            modifier = Modifier
                                .background(ZarpBubbleBg, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fileType, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (fileName.length > 18) fileName.take(18) + "..." else fileName,
                                color = ZarpTextPrimary, fontSize = 12.sp, maxLines = 1,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { onRemoveAttachment(index) }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Default.Close, "Remove", tint = ZarpTextTertiary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    ZarpInputBg,
                    if (attachedImageUris.isNotEmpty()) RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    else RoundedCornerShape(28.dp)
                )
                .border(1.5.dp, ZarpInputBorder.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { onAttachmentTap() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AttachFile, "Attach", tint = ZarpTextTertiary, modifier = Modifier.size(22.dp))
            }

            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onToggleSearchMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.TravelExplore, "Search", tint = if (isSearchMode) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(20.dp))
            }

            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onToggleTranslateMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Translate, "Translate", tint = if (isTranslateMode) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(20.dp))
            }

            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onToggleAgentMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, "Agent", tint = if (isAgentMode) Color(0xFF00E676) else ZarpTextTertiary, modifier = Modifier.size(20.dp))
            }

            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onToggleAutomationMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SettingsSuggest, "Automation", tint = if (isAutomationMode) Color(0xFFCE93D8) else ZarpTextTertiary, modifier = Modifier.size(20.dp))
            }

            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onToggleVoiceMode() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.KeyboardVoice, "Voice", tint = if (isVoiceMode) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                val placeholder = when {
                    isListening -> "🎤 Listening..."
                    isAutomationMode -> "🎮 Describe your automation..."
                    isAgentMode -> "🤖 Describe the app you want..."
                    isSearchMode && isTranslateMode -> "🔍🌍 Search + Translate..."
                    isSearchMode -> "🔍 Search the web..."
                    isTranslateMode -> "🌍 Type in any language..."
                    else -> "💬 Message Zarp..."
                }
                if (inputText.isEmpty()) Text(placeholder, color = ZarpTextTertiary, fontSize = 14.sp, maxLines = 1)
                if (!isListening) {
                    BasicTextField(
                        value = inputText, onValueChange = onInputChanged,
                        textStyle = TextStyle(color = ZarpTextPrimary, fontSize = 15.sp, lineHeight = 22.sp),
                        cursorBrush = SolidColor(ZarpAccent), maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Crossfade(
                targetState = when {
                    isThinking -> "pause"
                    isPaused -> "resume"
                    inputText.isNotBlank() || attachedImageUris.isNotEmpty() -> "send"
                    else -> "mic"
                },
                animationSpec = tween(250),
                label = "action_button"
            ) { state ->
                when (state) {
                    "pause" -> {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFFC107)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Pause, "Pause", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    "resume" -> {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(ZarpAccent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, "Resume", tint = ZarpMainBg, modifier = Modifier.size(22.dp))
                        }
                    }
                    "send" -> {
                        val sendColor = when {
                            isAutomationMode -> Color(0xFFCE93D8)
                            isAgentMode -> Color(0xFF00E676)
                            else -> ZarpAccent
                        }
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(sendColor.copy(alpha = sendGlow)).border(2.dp, sendColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowUpward, "Send", tint = ZarpMainBg, modifier = Modifier.size(20.dp))
                        }
                    }
                    else -> {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).scale(if (isListening) micPulse else 1f).clickable { onMicTap() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, "Mic", tint = if (isListening) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        if (isAgentMode || isAutomationMode || isVoiceMode || isSearchMode || isTranslateMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isAgentMode) ModeChip("🤖 Agent Mode", Color(0xFF00E676))
                if (isAutomationMode) ModeChip("🎮 Automation", Color(0xFFCE93D8))
                if (isVoiceMode) ModeChip("🎤 Voice Active", ZarpAccent)
                if (isSearchMode) ModeChip("🔍 Web Search", Color(0xFF80CBC4))
                if (isTranslateMode) ModeChip("🌍 Translate", Color(0xFFFFAB40))
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
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
