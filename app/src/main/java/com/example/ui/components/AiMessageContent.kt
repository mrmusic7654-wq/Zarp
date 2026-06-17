package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.ZarpCodeBg
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary
import kotlinx.coroutines.delay

@Composable
fun AiMessageContent(message: Message, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // We'll do a simple parsing for code blocks and inline code as requested.
        val parts = message.text.split("```")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) { // It's a code block
                CodeBlock(code = part)
            } else {
                Text(
                    text = part,
                    color = ZarpTextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        AiActionButtons()
    }
}

@Composable
fun CodeBlock(code: String) {
    val languageAndCode = code.split("\n", limit = 2)
    val lang = if (languageAndCode.size > 1) languageAndCode[0] else ""
    val actualCode = if (languageAndCode.size > 1) languageAndCode[1] else code

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ZarpCodeBg)
            .border(1.dp, com.example.ui.theme.ZarpDivider, RoundedCornerShape(8.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = lang, color = ZarpTextTertiary, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = ZarpTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Copy code", color = ZarpTextTertiary, fontSize = 12.sp)
                }
            }
            Text(
                text = actualCode,
                color = ZarpTextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun AiActionButtons() {
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500)
        show = true
    }

    if (show) {
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy",
                tint = ZarpTextTertiary,
                modifier = Modifier.size(18.dp)
            )
            Icon(
                imageVector = Icons.Outlined.ThumbUp,
                contentDescription = "Good response",
                tint = ZarpTextTertiary,
                modifier = Modifier.size(18.dp)
            )
            Icon(
                imageVector = Icons.Outlined.ThumbDown,
                contentDescription = "Bad response",
                tint = ZarpTextTertiary,
                modifier = Modifier.size(18.dp)
            )
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Regenerate",
                tint = ZarpTextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
