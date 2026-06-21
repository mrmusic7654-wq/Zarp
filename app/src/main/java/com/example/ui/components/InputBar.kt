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
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onToggleSearchMode: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
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
                            Text(fileType, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (fileName.length > 15) fileName.take(15) + "..." else fileName,
                                color = ZarpTextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { onRemoveAttachment(index) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Remove",
                                    tint = ZarpTextTertiary,
                                    modifier = Modifier.size(12.dp)
                                )
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
                .height(52.dp)
                .background(
                    ZarpInputBg,
                    if (attachedImageUris.isNotEmpty()) RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    else RoundedCornerShape(28.dp)
                )
                .border(1.dp, ZarpInputBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onAttachmentTap() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AttachFile,
                    "Attach",
                    tint = ZarpTextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Search toggle 🌐
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onToggleSearchMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.TravelExplore,
                    "Search web",
                    tint = if (isSearchMode) ZarpAccent else ZarpTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Translate toggle 🌍
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onToggleTranslateMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Translate,
                    "Translate",
                    tint = if (isTranslateMode) ZarpAccent else ZarpTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                val placeholder = when {
                    isListening -> "Listening..."
                    isSearchMode && isTranslateMode -> "Search + Translate..."
                    isSearchMode -> "Search the web..."
                    isTranslateMode -> "Type in any language..."
                    else -> "Message Zarp..."
                }

                if (inputText.isEmpty()) {
                    Text(placeholder, color = ZarpTextTertiary, fontSize = 15.sp)
                }
                if (!isListening) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChanged,
                        textStyle = TextStyle(color = ZarpTextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(ZarpAccent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Dynamic action button
            Crossfade(
                targetState = when {
                    isThinking -> "pause"
                    isPaused -> "resume"
                    inputText.isNotBlank() || attachedImageUris.isNotEmpty() -> "send"
                    else -> "mic"
                },
                animationSpec = tween(200),
                label = "action_button"
            ) { state ->
                when (state) {
                    "pause" -> {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFC107))
                                .clickable { onPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Pause, "Pause", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    "resume" -> {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ZarpAccent)
                                .clickable { onResume() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, "Resume", tint = ZarpMainBg, modifier = Modifier.size(20.dp))
                        }
                    }
                    "send" -> {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ZarpAccent)
                                .clickable { onSend() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowUpward, "Send", tint = ZarpMainBg, modifier = Modifier.size(18.dp))
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onMicTap() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, "Microphone", tint = ZarpTextTertiary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}
