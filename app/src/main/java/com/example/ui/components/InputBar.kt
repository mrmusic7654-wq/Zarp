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
    onRemoveAttachment: (Int) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
    ) {
        // ── Multiple attachment chips (compact) ──
        if (attachedImageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZarpInputBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachedImageUris.forEachIndexed { index, uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Attachment ${index + 1}",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemoveAttachment(index) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp)
                                .offset(x = 4.dp, y = (-4).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onAttachmentTap() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Attach",
                    tint = ZarpTextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                if (inputText.isEmpty() && !isListening) {
                    Text("Message Zarp...", color = ZarpTextTertiary, fontSize = 16.sp)
                } else if (isListening) {
                    Text("Listening...", color = ZarpTextTertiary, fontSize = 16.sp)
                }
                if (!isListening) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChanged,
                        textStyle = TextStyle(color = ZarpTextPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(ZarpAccent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // ── Dynamic button ──
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
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
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
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = ZarpMainBg,
                                modifier = Modifier.size(20.dp)
                            )
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
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = ZarpMainBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onMicTap() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Microphone",
                                tint = ZarpTextTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
