package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary

@Composable
fun MessageList(
    messages: List<Message>,
    isAiThinking: Boolean,
    modifier: Modifier = Modifier,
    speakingMessageId: String? = null,
    onSpeakMessage: ((String, String) -> Unit)? = null,
    likedMessages: Set<String> = emptySet(),
    dislikedMessages: Set<String> = emptySet(),
    onLikeMessage: ((String) -> Unit)? = null,
    onDislikeMessage: ((String) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    isSearchMode: Boolean = false,
    searchProgress: Float = 0f,
    searchEngines: Map<String, Int> = emptyMap()
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, isAiThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (messages.isEmpty() && !isAiThinking) {
        EmptyChatState(modifier = modifier)
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── Typing / Search indicator ──
            if (isAiThinking) {
                item(key = "typing_indicator") {
                    TypingIndicator(
                        isSearching = isSearchMode,
                        searchProgress = searchProgress,
                        searchEngines = searchEngines
                    )
                }
            }

            // ── Messages ──
            items(
                items = messages.reversed(),
                key = { it.id }
            ) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) +
                            slideInVertically(animationSpec = tween(300)) { it / 2 } +
                            scaleIn(animationSpec = tween(300), initialScale = 0.95f)
                ) {
                    if (message.isUser) {
                        UserMessageBubble(message = message)
                    } else {
                        val isLastAiMessage = messages.lastOrNull { !it.isUser }?.id == message.id

                        AiMessageContent(
                            message = message,
                            isSpeaking = speakingMessageId == message.id,
                            isLiked = message.id in likedMessages,
                            isDisliked = message.id in dislikedMessages,
                            onSpeak = if (onSpeakMessage != null) {
                                { onSpeakMessage(message.id, message.text) }
                            } else null,
                            onLike = if (onLikeMessage != null) {
                                { onLikeMessage(message.id) }
                            } else null,
                            onDislike = if (onDislikeMessage != null) {
                                { onDislikeMessage(message.id) }
                            } else null,
                            onRegenerate = if (isLastAiMessage && onRegenerate != null) {
                                { onRegenerate() }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Empty State
// ═══════════════════════════════════════════

@Composable
fun EmptyChatState(modifier: Modifier = Modifier) {
    // Fade-in animation for empty state
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Animated gradient icon
            val infiniteTransition = rememberInfiniteTransition(label = "empty_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    tween(2000, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(scale)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(ZarpAccent, Color(0xFF4A9EFF), Color(0xFF7BC8FF))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 32.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Zarp",
                    color = ZarpTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "How can I help you today?",
                    color = ZarpTextTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Feature hints
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.alpha(0.6f)
            ) {
                FeatureHint("🌐", "Search")
                FeatureHint("📎", "Upload")
                FeatureHint("🎤", "Voice")
                FeatureHint("🌍", "Translate")
            }
        }
    }
}

@Composable
private fun FeatureHint(emoji: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = ZarpTextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
