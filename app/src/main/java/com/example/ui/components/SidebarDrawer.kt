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
import com.example.data.ChatRepository
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
    tasks: List<Conversation> = emptyList(),
    onSelectTask: ((String) -> Unit)? = null,
    onDeleteTask: ((String) -> Unit)? = null,
    projects: List<ChatRepository.ProjectInfo> = emptyList(),
    onSelectProject: ((ChatRepository.ProjectInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var chatExpanded by remember { mutableStateOf(true) }
    var taskExpanded by remember { mutableStateOf(true) }
    var projectExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ZarpSidebarBg)
    ) {
        // ── Header ──
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Zarp", color = ZarpTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("AI Assistant", color = ZarpTextTertiary, fontSize = 13.sp)
        }

        // ── New Chat ──
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
                CollapsibleSectionHeader("💬 Chat History", conversations.size, chatExpanded) { chatExpanded = !chatExpanded }
            }

            if (chatExpanded) {
                if (conversations.isEmpty()) {
                    item { EmptyStateText("No conversations yet") }
                } else {
                    val grouped = conversations.groupBy { it.dateGroup }
                    grouped.forEach { (group, convs) ->
                        item { SectionLabel(group) }
                        items(convs, key = { "chat_${it.id}" }) { conv ->
                            HistoryItem(
                                icon = Icons.Default.ChatBubbleOutline,
                                title = conv.title,
                                isSelected = conv.id == currentConversationId,
                                onClick = { onSelectConversation(conv.id) },
                                onDelete = onDeleteConversation?.let { { it(conv.id) } }
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
                CollapsibleSectionHeader("🤖 Task History", tasks.size, taskExpanded) { taskExpanded = !taskExpanded }
            }

            if (taskExpanded) {
                if (tasks.isEmpty()) {
                    item { EmptyStateText("No tasks yet. Use Agent Mode.") }
                } else {
                    items(tasks, key = { "task_${it.id}" }) { task ->
                        HistoryItem(
                            icon = Icons.Default.Code,
                            title = task.title,
                            isSelected = task.id == currentConversationId,
                            onClick = { onSelectTask?.invoke(task.id) },
                            onDelete = onDeleteTask?.let { { it(task.id) } },
                            accentColor = Color(0xFF00E676)
                        )
                    }
                }
            }

            // ══════════════════════════════════════
            // PROJECTS
            // ══════════════════════════════════════
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = ZarpDivider, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(modifier = Modifier.height(4.dp))
                CollapsibleSectionHeader("🚀 Projects", projects.size, projectExpanded) { projectExpanded = !projectExpanded }
            }

            if (projectExpanded) {
                if (projects.isEmpty()) {
                    item { EmptyStateText("No projects yet. Agent Mode creates projects.") }
                } else {
                    items(projects, key = { "proj_${it.id}" }) { project ->
                        ProjectItem(
                            project = project,
                            onClick = { onSelectProject?.invoke(project) }
                        )
                    }
                }
            }
        }

        // ── Bottom ──
        Column {
            HorizontalDivider(color = ZarpDivider, thickness = 1.dp)
            SidebarActionItem(Icons.Outlined.Settings, "Settings", onSettingsTap)
            SidebarActionItem(Icons.Outlined.Star, "Zarp Pro", {})

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(ZarpAccent), contentAlignment = Alignment.Center) {
                    Text("U", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
// Components
// ═══════════════════════════════════════════

@Composable
private fun CollapsibleSectionHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = ZarpTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (count > 0) { Text("$count", color = ZarpTextTertiary, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp)) }
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Toggle", tint = ZarpTextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = ZarpTextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun EmptyStateText(text: String) {
    Text(text, color = ZarpTextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun HistoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    accentColor: Color = ZarpTextPrimary
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
        Icon(icon, null, tint = if (isSelected) accentColor else ZarpTextTertiary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = if (isSelected) accentColor else ZarpTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = ZarpTextTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ProjectItem(
    project: ChatRepository.ProjectInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (project.buildStatus) {
            "success" -> "✅"
            "failure" -> "❌"
            else -> "🚀"
        }
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(project.name, color = ZarpTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${project.filesCount} files • ${project.architectureType ?: "mvvm"}", color = ZarpTextTertiary, fontSize = 10.sp)
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
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ZarpTextPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, color = ZarpTextPrimary, fontSize = 15.sp)
    }
}
