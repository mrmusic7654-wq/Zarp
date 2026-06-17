package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpInputBg
import com.example.ui.theme.ZarpInputBorder
import com.example.ui.theme.ZarpMainBg
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary

@Composable
fun InputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
    onAttachmentTap: () -> Unit,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
            .height(52.dp)
            .background(ZarpInputBg, RoundedCornerShape(28.dp))
            .border(1.dp, ZarpInputBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            if (inputText.isEmpty() && !isListening) {
                Text(
                    text = "Message Zarp...",
                    color = ZarpTextTertiary,
                    fontSize = 16.sp
                )
            } else if (isListening) {
                Text(
                    text = "Listening...",
                    color = ZarpTextTertiary,
                    fontSize = 16.sp
                )
            }

            if (!isListening) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    textStyle = TextStyle(
                        color = ZarpTextPrimary,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(ZarpAccent),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Crossfade(
            targetState = inputText.isNotBlank(),
            animationSpec = tween(150),
            label = "send_mic_crossfade"
        ) { hasText ->
            if (hasText) {
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
            } else {
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
