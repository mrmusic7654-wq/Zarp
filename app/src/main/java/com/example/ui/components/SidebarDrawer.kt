package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Conversation
import com.example.ui.theme.*

@Composable
fun SidebarDrawer(
    conversations: List<Conversation>,
    currentConversationId: String?,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: ((String) -> Unit)? = null,
    onSettingsTap: () -> Unit,
    // ── Task History ──
    tasks: List<Conversation> = emptyList(),
    onSelectTask: ((String) -> Unit)? = null,
    onDeleteTask: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var chatExpanded by remember { mutableStateOf(true) }
    var taskExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ZarpSidebarBg)
    ) {
        // ── Top Section ──
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Zarp", color = ZarpTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("AI Assistant", color = ZarpTextTertiary, fontSize = 13.sp)
        }

        // ── New Chat Button ──
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
            Icon(Icons.Default.Add, "New chat", tint = ZarpTextPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("New chat", color = ZarpTextPrimary, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Scrollable Content ──
        LazyColumn(modifier = Modifier.weight(1f)) {

            // ══════════════════════════════════════
            // CHAT HISTORY
            // ══════════════════════════════════════
            item {
                CollapsibleSectionHeader(
                    title = "💬 Chat History",
                    count = conversations.size,
                    expanded = chatExpanded,
                    onToggle = { chatExpanded = !chatExpanded }
                )
            }

            if (chatExpanded) {
                if (conversations.isEmpty()) {
                    item {
                        Text(
                            "No conversations yet",
                            color = ZarpTextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    val grouped = conversations.groupBy { it.dateGroup }
                    grouped.forEach { (group, convs) ->
                        item {
                            Text(
                                group,
                                color = ZarpTextTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        items(convs, key = { "chat_${it.id}" }) { conv ->
                            ChatItem(
                                conv = conv,
                                isSelected = conv.id == currentConversationId,
                                onClick = { onSelectConversation(conv.id) },
                                onDelete = if (onDeleteConversation != null) {
                                    { onDeleteConversation(conv.id) }
                                } else null
                            )
                        }
                    }
                }
            }

            // ══════════════════════════════════════
            // TASK HISTORY
            // ══════════════════════════════════════
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = ZarpDivider, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(modifier = Modifier.height(4.dp))
                CollapsibleSectionHeader(
                    title = "🤖 Task History",
                    count = tasks.size,
                    expanded = taskExpanded,
                    onToggle = { taskExpanded = !taskExpanded }
                )
            }

            if (taskExpanded) {
                if (tasks.isEmpty()) {
                    item {
                        Text(
                            "No tasks yet. Use Agent Mode to create tasks.",
                            color = ZarpTextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(tasks, key = { "task_${it.id}" }) { task ->
                        TaskItem(
                            task = task,
                            isSelected = task.id == currentConversationId,
                            onClick = { onSelectTask?.invoke(task.id) },
                            onDelete = if (onDeleteTask != null) {
                                { onDeleteTask(task.id) }
                            } else null
                        )
                    }
                }
            }
        }

        // ── Bottom Section ──
        Column {
            HorizontalDivider(color = ZarpDivider, thickness = 1.dp)

            SidebarActionItem(icon = Icons.Outlined.Settings, text = "Settings", onClick = onSettingsTap)
            SidebarActionItem(icon = Icons.Outlined.Star, text = "Zarp Pro", onClick = {})

            // User Profile
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(ZarpTextTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("U", color = ZarpSidebarBg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("User", color = ZarpTextPrimary, fontSize = 14.sp)
                    Text("Free plan", color = ZarpTextTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Collapsible Section Header
// ═══════════════════════════════════════════

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = ZarpTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(
                "$count",
                color = ZarpTextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            "Toggle",
            tint = ZarpTextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ═══════════════════════════════════════════
// Chat Item
// ═══════════════════════════════════════════

@Composable
private fun ChatItem(
    conv: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ZarpBubbleBg else ZarpSidebarBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline,
            null,
            tint = if (isSelected) ZarpTextPrimary else ZarpTextTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            conv.title,
            color = if (isSelected) ZarpTextPrimary else ZarpTextSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = ZarpTextTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════
// Task Item
// ═══════════════════════════════════════════

@Composable
private fun TaskItem(
    task: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ZarpBubbleBg else ZarpSidebarBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🤖", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            task.title,
            color = if (isSelected) ZarpTextPrimary else ZarpTextSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = ZarpTextTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════
// Sidebar Action Item
// ═══════════════════════════════════════════

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
        Icon(icon, null, tint = ZarpTextPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, color = ZarpTextPrimary, fontSize = 15.sp)
    }
}
