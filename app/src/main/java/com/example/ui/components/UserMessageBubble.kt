package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZarpBubbleBg
import com.example.ui.theme.ZarpInputBorder
import com.example.ui.theme.ZarpTextPrimary
import com.example.model.Message

@Composable
fun UserMessageBubble(message: Message, modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val maxWidth = configuration.screenWidthDp.dp * 0.85f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(RoundedCornerShape(18.dp))
                .background(ZarpBubbleBg)
                .border(1.dp, ZarpInputBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    color = ZarpTextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
