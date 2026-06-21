package com.example.ui.screens

import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AgentLoopManager
import com.example.data.KeyManager
import com.example.data.UsageTracker
import com.example.ui.components.AttachmentSheet
import com.example.ui.components.InputBar
import com.example.ui.components.MessageList
import com.example.ui.components.SidebarDrawer
import com.example.ui.theme.*
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val drawerWidth = configuration.screenWidthDp.dp * 0.85f
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelSelector by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.onVoiceResult(text)
        else viewModel.onCancelVoice()
    }

    LaunchedEffect(Unit) {
        if (KeyManager.getApiKey(context).isNullOrBlank()) {
            snackbarHostState.showSnackbar("API key missing. Set it in Settings → API Keys.")
        }
    }

    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.isDrawerOpen && drawerState.isOpen) drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isDrawerOpen) viewModel.onToggleDrawer(drawerState.isOpen)
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    // ── Sheets & Dialogs ──
    if (uiState.showAttachmentSheet) {
        AttachmentSheet(
            onDismiss = { viewModel.dismissAttachmentSheet() },
            onImageSelected = { uri -> viewModel.onImageSelected(uri) },
            onImagesSelected = { uris -> viewModel.onImagesSelected(uris) },
            onFileSelected = { uri, name -> viewModel.onFileSelected(uri, name) },
            onFilesSelected = { uris -> viewModel.onFilesSelected(uris) }
        )
    }

    if (uiState.showStyleDialog) {
        var styleText by remember { mutableStateOf(uiState.customStyle) }
        AlertDialog(
            onDismissRequest = { viewModel.onDismissStyleDialog() },
            title = { Text("🎭 Custom Response Style", color = ZarpTextPrimary, fontSize = 18.sp) },
            text = {
                Column {
                    Text("Describe how you want Zarp to behave:", color = ZarpTextTertiary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = styleText, onValueChange = { styleText = it },
                        placeholder = { Text("e.g. Be brutally honest, roast me.", color = ZarpTextTertiary, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ZarpTextPrimary, unfocusedTextColor = ZarpTextPrimary, focusedBorderColor = ZarpAccent, unfocusedBorderColor = ZarpInputBorder, cursorColor = ZarpAccent),
                        minLines = 3)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onCustomStyleChanged(""); viewModel.onDismissStyleDialog() }) {
                        Text("🔄 Reset to default", color = ZarpTextTertiary)
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.onCustomStyleChanged(styleText); viewModel.onDismissStyleDialog() }, colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent)) { Text("✅ Apply", color = Color.White) } },
            dismissButton = { TextButton(onClick = { viewModel.onDismissStyleDialog() }) { Text("Cancel", color = ZarpTextTertiary) } },
            containerColor = ZarpSidebarBg
        )
    }

    if (uiState.showTranslateDialog && uiState.translateResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissTranslateDialog() },
            title = { Text("🌐 Translation", color = ZarpTextPrimary, fontSize = 18.sp) },
            text = { SelectionContainer { Text(uiState.translateResult ?: "", color = ZarpTextPrimary, fontSize = 15.sp, lineHeight = 24.sp) } },
            confirmButton = { Button(onClick = { viewModel.onDismissTranslateDialog() }, colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent)) { Text("OK", color = Color.White) } },
            containerColor = ZarpSidebarBg
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState, gesturesEnabled = true, scrimColor = Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            Box(modifier = Modifier.width(drawerWidth)) {
                SidebarDrawer(
                    conversations = uiState.conversations, currentConversationId = uiState.currentConversationId,
                    onNewChat = { viewModel.onNewChat(); scope.launch { drawerState.close() } },
                    onSelectConversation = { viewModel.onSelectConversation(it); scope.launch { drawerState.close() } },
                    onDeleteConversation = { viewModel.onDeleteConversation(it) },
                    onSettingsTap = { scope.launch { drawerState.close() }; onNavigateToSettings() }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showModelSelector = true }.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(uiState.selectedModel, color = ZarpTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ExpandMore, "Model", tint = ZarpAccent, modifier = Modifier.padding(start = 2.dp).size(16.dp))
                            }
                            DropdownMenu(expanded = showModelSelector, onDismissRequest = { showModelSelector = false }) {
                                ChatViewModel.availableModels.forEach { model ->
                                    val used = UsageTracker.getCount(context, model); val limit = UsageTracker.getLimit(model); val percentage = if (limit > 0) (used * 100 / limit).coerceIn(0, 100) else 0
                                    DropdownMenuItem(text = {
                                        Column(modifier = Modifier.width(200.dp)) {
                                            Text(model, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (uiState.selectedModel == model) ZarpAccent else ZarpTextPrimary)
                                            Text("$used / $limit today", fontSize = 10.sp, color = ZarpTextTertiary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFF444444))) {
                                                Box(Modifier.fillMaxWidth(percentage / 100f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(when { percentage > 80 -> Color(0xFFFF5252); percentage > 50 -> Color(0xFFFFC107); else -> ZarpAccent }))
                                            }
                                        }
                                    }, onClick = { viewModel.onModelSelected(model); showModelSelector = false })
                                }
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu", tint = ZarpTextPrimary) } },
                    actions = {
                        AnimatedVisibility(visible = uiState.isAiThinking || uiState.isPaused, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            IconButton(onClick = { viewModel.onStopGeneration() }) {
                                Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFF5252)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Stop, "Stop", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.onShowStyleDialog() }) { Icon(Icons.Outlined.AutoAwesome, "Style", tint = if (uiState.customStyle.isNotBlank()) ZarpAccent else ZarpTextTertiary, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { viewModel.onNewChat() }) { Icon(Icons.Outlined.Edit, "New", tint = ZarpTextPrimary) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ZarpMainBg, scrolledContainerColor = ZarpMainBg)
                )
            },
            containerColor = ZarpMainBg
        ) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues)) {

                // ── Agent Progress Panel ──
                AnimatedVisibility(
                    visible = uiState.isAgentMode && uiState.agentProgress != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    uiState.agentProgress?.let { progress ->
                        AgentProgressPanel(progress = progress, agentResult = uiState.agentTaskResult)
                    }
                }

                // ── Messages ──
                MessageList(
                    messages = uiState.messages, isAiThinking = uiState.isAiThinking, modifier = Modifier.weight(1f),
                    speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = { id, text -> viewModel.onSpeakMessage(id, text) },
                    likedMessages = uiState.likedMessages, dislikedMessages = uiState.dislikedMessages,
                    onLikeMessage = { viewModel.onLikeMessage(it) },
                    onDislikeMessage = { viewModel.onDislikeMessage(it) },
                    onRegenerate = { viewModel.onRegenerate() },
                    isSearchMode = uiState.isSearchMode,
                    searchProgress = if (uiState.isAiThinking && uiState.isSearchMode) 0.5f else 0f,
                    searchEngines = emptyMap()
                )

                // ── Input Bar ──
                InputBar(
                    inputText = uiState.inputText,
                    onInputChanged = { viewModel.onInputChanged(it) },
                    onSend = { viewModel.onSend() },
                    onMicTap = { viewModel.onStartVoiceInput(voiceLauncher) },
                    onAttachmentTap = { viewModel.onAttachmentTap() },
                    isListening = uiState.isListening,
                    isThinking = uiState.isAiThinking && !uiState.isPaused,
                    isPaused = uiState.isPaused,
                    onPause = { viewModel.onPauseGeneration() },
                    onResume = { viewModel.onResumeGeneration() },
                    attachedImageUris = uiState.selectedImageUris,
                    attachedFileNames = uiState.selectedFileNames,
                    attachedFileTypes = uiState.selectedFileTypes,
                    onRemoveAttachment = { index -> viewModel.removeSingleAttachment(index) },
                    isTranslateMode = uiState.isTranslateMode,
                    onToggleTranslateMode = { viewModel.onToggleTranslateMode() },
                    isSearchMode = uiState.isSearchMode,
                    onToggleSearchMode = { viewModel.onToggleSearchMode() },
                    isAgentMode = uiState.isAgentMode,
                    onToggleAgentMode = { viewModel.onToggleAgentMode() }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
// Agent Progress Panel
// ═══════════════════════════════════════════

@Composable
private fun AgentProgressPanel(
    progress: AgentLoopManager.AgentProgress,
    agentResult: AgentLoopManager.AgentResult?
) {
    val statusColor = when (progress.status) {
        AgentLoopManager.AgentStatus.COMPLETED -> Color(0xFF00E676)
        AgentLoopManager.AgentStatus.FAILED -> Color(0xFFFF5252)
        else -> ZarpAccent
    }

    val statusEmoji = when (progress.status) {
        AgentLoopManager.AgentStatus.PLANNING -> "🧠"
        AgentLoopManager.AgentStatus.EXECUTING -> "⚡"
        AgentLoopManager.AgentStatus.REVIEWING -> "🔍"
        AgentLoopManager.AgentStatus.FIXING -> "🔧"
        AgentLoopManager.AgentStatus.PUSHING -> "📤"
        AgentLoopManager.AgentStatus.COMPLETED -> "✅"
        AgentLoopManager.AgentStatus.FAILED -> "❌"
        else -> "🤖"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1F0D))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusEmoji, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (progress.status) {
                        AgentLoopManager.AgentStatus.PLANNING -> "Planning task..."
                        AgentLoopManager.AgentStatus.EXECUTING -> "Executing step ${progress.currentStep}/${progress.totalSteps}"
                        AgentLoopManager.AgentStatus.REVIEWING -> "Reviewing code..."
                        AgentLoopManager.AgentStatus.FIXING -> "Fixing issues..."
                        AgentLoopManager.AgentStatus.PUSHING -> "Pushing to GitHub..."
                        AgentLoopManager.AgentStatus.COMPLETED -> "Task completed!"
                        AgentLoopManager.AgentStatus.FAILED -> "Task failed"
                        else -> progress.stepDescription
                    },
                    color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Text(progress.message, color = Color(0xFF81C784), fontSize = 11.sp)
            }
            Text("${(progress.percentage * 100).toInt()}%", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        if (progress.status != AgentLoopManager.AgentStatus.COMPLETED && progress.status != AgentLoopManager.AgentStatus.FAILED) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.percentage },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = statusColor, trackColor = Color(0xFF1A3A1A)
            )
        }

        // Show result summary when completed
        if (progress.status == AgentLoopManager.AgentStatus.COMPLETED && agentResult != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(agentResult.summary, color = Color(0xFFC8E6C9), fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (agentResult.repoUrl != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("🔗 ${agentResult.repoUrl}", color = ZarpAccent, fontSize = 10.sp)
            }
        }
    }
}
