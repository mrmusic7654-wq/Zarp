package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.ZarpCodeBg
import com.example.ui.theme.ZarpDivider
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextSecondary
import com.example.ui.theme.ZarpTextTertiary
import kotlinx.coroutines.delay

@Composable
fun AiMessageContent(message: Message, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showActions = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        SelectionContainer {
            Column {
                val parts = message.text.split("```")
                parts.forEachIndexed { index, part ->
                    if (index % 2 == 1) {
                        // Code block
                        CodeBlock(code = part)
                    } else {
                        // Regular text with formatting
                        FormattedText(text = part)
                    }
                }
            }
        }

        if (showActions) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(message.text)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = ZarpTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = "Good response",
                        tint = ZarpTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbDown,
                        contentDescription = "Bad response",
                        tint = ZarpTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Regenerate",
                        tint = ZarpTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FormattedText(text: String) {
    val formattedText = buildAnnotatedString {
        var remaining = text.trim()
        
        while (remaining.isNotEmpty()) {
            when {
                // Bold text **...**
                remaining.startsWith("**") -> {
                    val end = remaining.indexOf("**", 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ZarpTextPrimary)) {
                            append(remaining.substring(2, end))
                        }
                        remaining = remaining.substring(end + 2)
                    } else {
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    }
                }
                // Italic text *...*
                remaining.startsWith("*") -> {
                    val end = remaining.indexOf("*", 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ZarpTextPrimary)) {
                            append(remaining.substring(1, end))
                        }
                        remaining = remaining.substring(end + 1)
                    } else {
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    }
                }
                // Bullet points
                remaining.startsWith("• ") || remaining.startsWith("- ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = ZarpAccent)) {
                        append("  •  ")
                    }
                    remaining = remaining.substring(2)
                }
                else -> {
                    append(remaining[0])
                    remaining = remaining.substring(1)
                }
            }
        }
    }
    
    Text(
        text = formattedText,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        color = ZarpTextPrimary
    )
}

@Composable
fun CodeBlock(code: String) {
    val clipboardManager = LocalClipboardManager.current
    val languageAndCode = code.split("\n", limit = 2)
    val lang = if (languageAndCode.size > 1) languageAndCode[0].trim() else "code"
    val actualCode = if (languageAndCode.size > 1) languageAndCode[1] else code

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ZarpCodeBg)
            .border(1.dp, ZarpDivider, RoundedCornerShape(8.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang,
                    color = ZarpTextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(actualCode.trim())) },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = ZarpTextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = actualCode.trim(),
                    color = ZarpTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
