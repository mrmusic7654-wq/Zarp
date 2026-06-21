package com.example.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
private val LikeRed = Color(0xFFFF5252)
private val DislikeRed = Color(0xFFFF5252)
private val InlineCodeBg = Color(0xFF2A2A40)
private val InlineCodeColor = Color(0xFFE0A0FF)
private val TableColor = Color(0xFFC0C0FF)

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
            enter = expandVertically() + androidx.compose.animation.fadeIn()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                // Copy
                ActionButton(
                    icon = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    onClick = { clipboardManager.setText(AnnotatedString(message.text)) }
                )

                // Speak
                if (onSpeak != null) {
                    ActionButton(
                        icon = Icons.Outlined.VolumeUp,
                        contentDescription = "Speak",
                        isActive = isSpeaking,
                        activeColor = ZarpAccent,
                        onClick = onSpeak
                    )
                }

                // Like
                if (onLike != null) {
                    ActionButton(
                        icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        isActive = isLiked,
                        activeColor = ZarpAccent,
                        onClick = onLike
                    )
                }

                // Dislike
                if (onDislike != null) {
                    ActionButton(
                        icon = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Dislike",
                        isActive = isDisliked,
                        activeColor = DislikeRed,
                        onClick = onDislike
                    )
                }

                // Regenerate
                if (onRegenerate != null) {
                    ActionButton(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "Regenerate",
                        onClick = onRegenerate
                    )
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
    onClick: () -> Unit,
    isActive: Boolean = false,
    activeColor: Color = ZarpAccent
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
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
    var expanded by remember { mutableStateOf(true) } // Start expanded for visibility

    // Collapse after 3 seconds
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
        // Header
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
                text = "Searched ${sources.size} website${if (sources.size != 1) "s" else ""}",
                color = SearchHeaderText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = "Toggle sources",
                tint = SearchHeaderText,
                modifier = Modifier.size(18.dp)
            )
        }

        // Source list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            ) {
                HorizontalDivider(
                    color = SearchBorder,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                sources.forEachIndexed { index, (title, url) ->
                    SourceItem(
                        index = index + 1,
                        title = title,
                        url = url,
                        onClick = {
                            try {
                                uriHandler.openUri(url)
                            } catch (_: Exception) {}
                        }
                    )
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
            text = "$index.",
            color = SearchNumberText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "Untitled" },
                color = SearchTitleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = url,
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
    // Match BOTH formats:
    // 1. [Source: title](url)
    // 2. [Source: title - url]
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
                Text(
                    text = "Deep Think",
                    color = ThinkText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                color = ThinkContent,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace
            )
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
                remaining.startsWith("|") -> {
                    val newline = remaining.indexOf("\n")
                    val line = if (newline != -1) remaining.substring(0, newline) else remaining
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TableColor)) {
                        append(line)
                    }
                    remaining = if (newline != -1) remaining.substring(newline) else ""
                }
                remaining.startsWith("• ") || remaining.startsWith("- ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = AccentBlue)) {
                        append("  •  ")
                    }
                    remaining = remaining.substring(2)
                }
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
            // Code header
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
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = ZarpTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Code content
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
