package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.*
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════
// Color Constants
// ═══════════════════════════════════════════
private val AccentBlue = Color(0xFF75B6FF)
private val ThinkBg = Color(0xFF1A1A2E)
private val ThinkBorder = Color(0xFF4A4A7F)
private val ThinkText = Color(0xFF9B9BFF)
private val ThinkContent = Color(0xFFC0C0E0)
private val CodeHeaderBg = Color(0xFF252540)
private val SearchBg = Color(0xFF0D1F0D)
private val SearchBorder = Color(0xFF1A3A1A)
private val SearchHeaderText = Color(0xFF8FDF8F)
private val SearchTitleText = Color(0xFFC8E6C9)
private val SearchUrlText = Color(0xFF81C784)
private val SearchNumberText = Color(0xFF6AAF6A)
private val DislikeRed = Color(0xFFFF5252)
private val InlineCodeBg = Color(0xFF2A2A40)
private val InlineCodeColor = Color(0xFFE0A0FF)
private val TableColor = Color(0xFFC0C0FF)
private val TableCellBg = Color(0xFF1E1E30)

// ═══════════════════════════════════════════
// Main Composable
// ═══════════════════════════════════════════

@Composable
fun AiMessageContent(
    message: Message,
    modifier: Modifier = Modifier,
    isSpeaking: Boolean = false,
    isLiked: Boolean = false,
    isDisliked: Boolean = false,
    onSpeak: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    onDislike: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        delay(250)
        showActions = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // ── WEB SOURCES PANEL ──
        val sources = extractSources(message.text)
        if (sources.isNotEmpty()) {
            WebSourcesPanel(sources = sources, uriHandler = uriHandler)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── MESSAGE CONTENT ──
        SelectionContainer {
            Column {
                val content = stripSources(message.text)
                val thinkRegex = Regex("\\[THINKING\\](.*?)\\[\\/THINKING\\]", RegexOption.DOT_MATCHES_ALL)
                val parts = content.split(thinkRegex)
                val matches = thinkRegex.findAll(content).toList()
                var matchIndex = 0

                parts.forEachIndexed { index, part ->
                    if (part.isNotBlank()) {
                        RenderWithCodeBlocks(part)
                    }
                    if (matchIndex < matches.size) {
                        DeepThinkBlock(matches[matchIndex].groupValues[1].trim())
                        matchIndex++
                    }
                }
            }
        }

        // ── ACTION BUTTONS ──
        AnimatedVisibility(
            visible = showActions,
            enter = expandVertically() + fadeIn()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                ActionButton(Icons.Outlined.ContentCopy, "Copy") {
                    clipboardManager.setText(AnnotatedString(message.text))
                }
                if (onSpeak != null) {
                    ActionButton(Icons.Outlined.VolumeUp, "Speak", isSpeaking, ZarpAccent, onSpeak)
                }
                if (onLike != null) {
                    ActionButton(
                        if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        "Like", isLiked, ZarpAccent, onLike
                    )
                }
                if (onDislike != null) {
                    ActionButton(
                        if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        "Dislike", isDisliked, DislikeRed, onDislike
                    )
                }
                if (onRegenerate != null) {
                    ActionButton(Icons.Outlined.Refresh, "Regenerate", onClick = onRegenerate)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Action Button
// ═══════════════════════════════════════════

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    activeColor: Color = ZarpAccent,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            icon,
            contentDescription,
            tint = if (isActive) activeColor else ZarpTextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ═══════════════════════════════════════════
// Web Sources Panel
// ═══════════════════════════════════════════

@Composable
private fun WebSourcesPanel(
    sources: List<Pair<String, String>>,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(sources) {
        delay(3000)
        expanded = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SearchBg)
            .border(1.dp, SearchBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌐", fontSize = 15.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Searched ${sources.size} website${if (sources.size != 1) "s" else ""}",
                color = SearchHeaderText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                "Toggle",
                tint = SearchHeaderText,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(300)),
            exit = shrinkVertically(tween(200))
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                HorizontalDivider(color = SearchBorder, modifier = Modifier.padding(vertical = 6.dp))
                sources.forEachIndexed { i, (title, url) ->
                    SourceItem(i + 1, title, url) {
                        try { uriHandler.openUri(url) } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItem(
    index: Int,
    title: String,
    url: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$index.",
            color = SearchNumberText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title.ifBlank { "Untitled" },
                color = SearchTitleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                url,
                color = SearchUrlText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

// ═══════════════════════════════════════════
// Source Extraction
// ═══════════════════════════════════════════

private fun extractSources(text: String): List<Pair<String, String>> {
    val regex = Regex(
        "\\[Source:\\s*(.+?)\\]\\((https?://[^\\)]+)\\)" +
        "|" +
        "\\[Source:\\s*(.+?)\\s*-\\s*(https?://[^\\]]+)\\]"
    )
    return regex.findAll(text).mapNotNull { match ->
        val g = match.groupValues
        when {
            g[1].isNotBlank() && g[2].isNotBlank() -> Pair(g[1].trim(), g[2].trim())
            g[3].isNotBlank() && g[4].isNotBlank() -> Pair(g[3].trim(), g[4].trim())
            else -> null
        }
    }.toList()
}

private fun stripSources(text: String): String {
    return text
        .replace(Regex("\\[Source:.*?\\]\\(https?://[^\\)]+\\)\\n?"), "")
        .replace(Regex("\\[Source:.*?\\]\\n?"), "")
        .trim()
}

// ═══════════════════════════════════════════
// Deep Think Block
// ═══════════════════════════════════════════

@Composable
fun DeepThinkBlock(content: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ThinkBg)
            .border(1.dp, ThinkBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deep Think", color = ThinkText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, color = ThinkContent, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ═══════════════════════════════════════════
// Code & Text Rendering
// ═══════════════════════════════════════════

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
fun FormattedText(text: String) {
    val formattedText = buildAnnotatedString {
        var remaining = text.trim()

        while (remaining.isNotEmpty()) {
            when {
                // Inline code
                remaining.startsWith("`") -> {
                    val end = remaining.indexOf("`", 1)
                    if (end != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = InlineCodeBg,
                            color = InlineCodeColor,
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
                // ── TABLE RENDERING (FIXED) ──
                remaining.startsWith("|") -> {
                    val newline = remaining.indexOf("\n")
                    val line = if (newline != -1) remaining.substring(0, newline) else remaining
                    val isSeparator = line.replace("|", "").all { it in setOf('-', ':', ' ') }
                    if (isSeparator) {
                        withStyle(SpanStyle(fontSize = 1.sp, color = Color.Transparent)) {
                            append(line)
                        }
                    } else {
                        val cells = line.split("|").filter { it.isNotBlank() }
                        cells.forEach { cell ->
                            withStyle(SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = ZarpTextPrimary,
                                background = TableCellBg
                            )) {
                                append(" ${cell.trim()} ")
                            }
                            withStyle(SpanStyle(color = ZarpDivider)) { append("│") }
                        }
                    }
                    remaining = if (newline != -1) remaining.substring(newline + 1) else ""
                }
                // Bullet points
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
    else code.trim()

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
                        lang,
                        color = ZarpTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(actualCode)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        "Copy code",
                        tint = ZarpTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            SelectionContainer {
                Text(
                    actualCode,
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
