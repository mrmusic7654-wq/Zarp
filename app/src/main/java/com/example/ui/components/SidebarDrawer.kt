package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Conversation
import com.example.ui.theme.ZarpBubbleBg
import com.example.ui.theme.ZarpDivider
import com.example.ui.theme.ZarpSidebarBg
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextSecondary
import com.example.ui.theme.ZarpTextTertiary

@Composable
fun SidebarDrawer(
    conversations: List<Conversation>,
    currentConversationId: String?,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onSettingsTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ZarpSidebarBg)
    ) {
        // Top Section
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Zarp",
                color = ZarpTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "AI Assistant",
                color = ZarpTextTertiary,
                fontSize = 13.sp
            )
        }

        // New Chat Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ZarpBubbleBg)
                .clickable { onNewChat() }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New chat",
                tint = ZarpTextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "New chat",
                color = ZarpTextPrimary,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat History Section
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            val grouped = conversations.groupBy { it.dateGroup }
            grouped.forEach { (group, convs) ->
                item {
                    Text(
                        text = group,
                        color = ZarpTextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(convs, key = { it.id }) { conv ->
                    val isSelected = conv.id == currentConversationId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ZarpBubbleBg else ZarpSidebarBg)
                            .clickable { onSelectConversation(conv.id) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = ZarpTextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = conv.title,
                            color = ZarpTextPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Bottom Section
        Column {
            HorizontalDivider(color = ZarpDivider, thickness = 1.dp)
            
            SidebarActionItem(
                icon = Icons.Outlined.Settings,
                text = "Settings",
                onClick = onSettingsTap
            )
            
            SidebarActionItem(
                icon = Icons.Outlined.Star,
                text = "Zarp Pro",
                onClick = {}
            )

            // User Profile Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ZarpTextTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "U", color = ZarpTextPrimary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "User", color = ZarpTextPrimary, fontSize = 14.sp)
                    Text(text = "Free plan", color = ZarpTextTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SidebarActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ZarpTextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = ZarpTextPrimary,
            fontSize = 15.sp
        )
    }
}
