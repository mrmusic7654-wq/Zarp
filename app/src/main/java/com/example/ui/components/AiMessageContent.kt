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

private val AccentBlue = Color(0xFF75B6FF)
private val ThinkBg = Color(0xFF1A1A2E)
private val ThinkBorder = Color(0xFF4A4A7F)
private val TableBg = Color(0xFF1E1E2E)
private val CodeHeaderBg = Color(0xFF252540)

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
                val content = message.text

                // Split by thinking blocks
                val thinkRegex = Regex("\\[THINKING\\](.*?)\\[\\/THINKING\\]", RegexOption.DOT_MATCHES_ALL)
                val parts = content.split(thinkRegex)
                
                var isFirst = true
                val matches = thinkRegex.findAll(content).toList()
                var matchIndex = 0

                parts.forEachIndexed { index, part ->
                    if (part.isNotBlank()) {
                        // Render code blocks within this part
                        RenderWithCodeBlocks(part)
                    }
                    // Insert thinking block after this part if available
                    if (matchIndex < matches.size) {
                        DeepThinkBlock(matches[matchIndex].groupValues[1].trim())
                        matchIndex++
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
                        contentDescription = "Copy response",
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
fun RenderWithCodeBlocks(text: String) {
    val parts = text.split("```")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            CodeBlock(code = part)
        } else if (part.isNotBlank()) {
            FormattedText(text = part)
        }
    }
}

@Composable
fun DeepThinkBlock(content: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ThinkBg)
            .border(1.dp, ThinkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Deep Think",
                    color = Color(0xFF9B9BFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                color = Color(0xFFC0C0E0),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun FormattedText(text: String) {
    val formattedText = buildAnnotatedString {
        var remaining = text.trim()

        while (remaining.isNotEmpty()) {
            when {
                // Code inline
                remaining.startsWith("`") -> {
                    val end = remaining.indexOf("`", 1)
                    if (end != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF2A2A40),
                            color = Color(0xFFE0A0FF),
                            fontSize = 13.sp
                        )) {
                            append(remaining.substring(1, end))
                        }
                        remaining = remaining.substring(end + 1)
                    } else {
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    }
                }
                // Bold
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
                // Italic
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
                // Table row
                remaining.startsWith("|") -> {
                    val newline = remaining.indexOf("\n")
                    val line = if (newline != -1) remaining.substring(0, newline) else remaining
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFC0C0FF)
                    )) {
                        append(line)
                    }
                    remaining = if (newline != -1) remaining.substring(newline) else ""
                }
                // Bullet
                remaining.startsWith("• ") || remaining.startsWith("- ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = AccentBlue)) {
                        append("  •  ")
                    }
                    remaining = remaining.substring(2)
                }
                // Numbered list
                remaining.first().isDigit() && remaining.contains(". ") && remaining.indexOf(". ") in 1..3 -> {
                    val dotIndex = remaining.indexOf(". ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AccentBlue)) {
                        append(remaining.substring(0, dotIndex + 2))
                    }
                    remaining = remaining.substring(dotIndex + 2)
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
    val lines = code.split("\n")
    val lang = lines.firstOrNull()?.trim()?.takeIf { it.length < 20 } ?: "code"
    val actualCode = if (lines.size > 1 && lines.first().trim().length < 20) 
        lines.drop(1).joinToString("\n").trim() 
    else 
        code.trim()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZarpCodeBg)
            .border(1.dp, ZarpDivider, RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeHeaderBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📄", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = lang,
                        color = ZarpTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(actualCode)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = ZarpTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = actualCode,
                    color = Color(0xFFD4D4FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
