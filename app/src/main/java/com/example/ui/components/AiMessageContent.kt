package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpCodeBg
import com.example.ui.theme.ZarpDivider
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextSecondary
import com.example.ui.theme.ZarpTextTertiary
import kotlinx.coroutines.delay

private val AccentBlue = Color(0xFF75B6FF)
private val ThinkBg = Color(0xFF1A1A2E)
private val ThinkBorder = Color(0xFF4A4A7F)
private val CodeHeaderBg = Color(0xFF252540)
private val SearchBg = Color(0xFF1A2E1A)
private val SearchBorder = Color(0xFF2E5A2E)

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
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        delay(200)
        showActions = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // ── Web sources panel (collapsible) ──
        val sources = extractSources(message.text)
        if (sources.isNotEmpty()) {
            WebSourcesPanel(sources = sources)
            Spacer(modifier = Modifier.height(6.dp))
        }

        // ── Message content ──
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

        // ── Action buttons ──
        if (showActions) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(message.text)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copy", tint = ZarpTextTertiary, modifier = Modifier.size(18.dp))
                }
                if (onSpeak != null) {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.VolumeUp, "Speak", tint = if (isSpeaking) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
                if (onLike != null) {
                    IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.ThumbUp, "Like", tint = if (isLiked) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
                if (onDislike != null) {
                    IconButton(onClick = onDislike, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.ThumbDown, "Dislike", tint = if (isDisliked) Color(0xFFFF5252) else ZarpTextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
                if (onRegenerate != null) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Refresh, "Regenerate", tint = ZarpTextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── Web Sources Collapsible Panel ──
@Composable
private fun WebSourcesPanel(sources: List<Pair<String, String>>) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SearchBg)
            .border(1.dp, SearchBorder, RoundedCornerShape(10.dp))
    ) {
        // Header row — always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌐", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Searched ${sources.size} website${if (sources.size > 1) "s" else ""}",
                color = Color(0xFF8FDF8F),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                "Toggle sources",
                tint = Color(0xFF8FDF8F),
                modifier = Modifier.size(16.dp)
            )
        }

        // Expandable source list
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            ) {
                HorizontalDivider(color = SearchBorder, modifier = Modifier.padding(vertical = 4.dp))
                sources.forEachIndexed { index, (title, url) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "${index + 1}.",
                            color = Color(0xFF6AAF6A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(16.dp)
                        )
                        Column {
                            Text(
                                title.ifBlank { "Untitled" },
                                color = Color(0xFFC8E6C9),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                url,
                                color = Color(0xFF81C784),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Extract [Source: title - url] pairs from response ──
private fun extractSources(text: String): List<Pair<String, String>> {
    val regex = Regex("\\[Source:\\s*(.+?)\\s*-\\s*(https?://[^\\]]+)\\]")
    return regex.findAll(text).map { match ->
        Pair(match.groupValues[1].trim(), match.groupValues[2].trim())
    }.toList()
}

// ── Strip source lines from displayed text ──
private fun stripSources(text: String): String {
    return text.replace(Regex("\\[Source:.*?\\]\\n?"), "").trim()
}

// ── Deep Think Block ──
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
                Text("Deep Think", color = Color(0xFF9B9BFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, color = Color(0xFFC0C0E0), fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ── Code block rendering ──
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
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2A2A40), color = Color(0xFFE0A0FF), fontSize = 13.sp)) {
                            append(remaining.substring(1, end))
                        }
                        remaining = remaining.substring(end + 1)
                    } else { append(remaining[0]); remaining = remaining.substring(1) }
                }
                remaining.startsWith("**") -> {
                    val end = remaining.indexOf("**", 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ZarpTextPrimary)) { append(remaining.substring(2, end)) }
                        remaining = remaining.substring(end + 2)
                    } else { append(remaining[0]); remaining = remaining.substring(1) }
                }
                remaining.startsWith("*") -> {
                    val end = remaining.indexOf("*", 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ZarpTextPrimary)) { append(remaining.substring(1, end)) }
                        remaining = remaining.substring(end + 1)
                    } else { append(remaining[0]); remaining = remaining.substring(1) }
                }
                remaining.startsWith("|") -> {
                    val newline = remaining.indexOf("\n")
                    val line = if (newline != -1) remaining.substring(0, newline) else remaining
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFC0C0FF))) { append(line) }
                    remaining = if (newline != -1) remaining.substring(newline) else ""
                }
                remaining.startsWith("• ") || remaining.startsWith("- ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = AccentBlue)) { append("  •  ") }
                    remaining = remaining.substring(2)
                }
                remaining.first().isDigit() && remaining.contains(". ") && remaining.indexOf(". ") in 1..3 -> {
                    val dotIndex = remaining.indexOf(". ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AccentBlue)) { append(remaining.substring(0, dotIndex + 2)) }
                    remaining = remaining.substring(dotIndex + 2)
                }
                else -> { append(remaining[0]); remaining = remaining.substring(1) }
            }
        }
    }
    Text(formattedText, fontSize = 15.sp, lineHeight = 24.sp, color = ZarpTextPrimary)
}

@Composable
fun CodeBlock(code: String) {
    val clipboardManager = LocalClipboardManager.current
    val lines = code.split("\n")
    val lang = lines.firstOrNull()?.trim()?.takeIf { it.length < 20 } ?: "code"
    val actualCode = if (lines.size > 1 && lines.first().trim().length < 20) lines.drop(1).joinToString("\n").trim() else code.trim()

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
                modifier = Modifier.fillMaxWidth().background(CodeHeaderBg).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📄", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lang, color = ZarpTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(actualCode)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copy", tint = ZarpTextSecondary, modifier = Modifier.size(14.dp))
                }
            }
            SelectionContainer {
                Text(actualCode, color = Color(0xFFD4D4FF), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
