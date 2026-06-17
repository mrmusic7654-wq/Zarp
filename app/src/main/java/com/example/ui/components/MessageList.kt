package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary

@Composable
fun MessageList(
    messages: List<Message>,
    isAiThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

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
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (isAiThinking) {
                item {
                    TypingIndicator()
                }
            }
            
            items(messages.reversed(), key = { it.id }) { message ->
                if (message.isUser) {
                    UserMessageBubble(message = message)
                } else {
                    AiMessageContent(message = message)
                }
            }
        }
    }
}

@Composable
fun EmptyChatIcon() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        com.example.ui.theme.ZarpAccent,
                        androidx.compose.ui.graphics.Color(0xFF4A9EFF)
                    )
                ),
                shape = androidx.compose.foundation.shape.CircleShape
            )
    )
}

@Composable
fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyChatIcon()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Zarp",
                color = ZarpTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "How can I help you today?",
                color = ZarpTextTertiary,
                fontSize = 14.sp
            )
        }
    }
}
