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
import com.example.data.AutomationEngine
import com.example.data.BuildMonitor
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
    var agentPanelExpanded by remember { mutableStateOf(true) }
    var autoPanelExpanded by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.onVoiceResult(text)
        else viewModel.onCancelVoice()
    }

    // Error snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long
            )
            viewModel.onDismissError()
        }
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
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ZarpTextPrimary, unfocusedTextColor = ZarpTextPrimary, focusedBorderColor = ZarpAccent, unfocusedBorderColor = ZarpInputBorder, cursorColor = ZarpAccent), minLines = 3)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onCustomStyleChanged(""); viewModel.onDismissStyleDialog() }) { Text("🔄 Reset to default", color = ZarpTextTertiary) }
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
                    onSettingsTap = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                    tasks = uiState.tasks, onSelectTask = { viewModel.onSelectTask(it); scope.launch { drawerState.close() } },
                    onDeleteTask = { viewModel.onDeleteTask(it) },
                    projects = uiState.projects, onSelectProject = { viewModel.onSelectProject(it); scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = ZarpBubbleBg,
                        contentColor = ZarpTextPrimary,
                        actionColor = ZarpAccent,
                        shape = RoundedCornerShape(12.dp),
                        actionOnNewLine = true
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showModelSelector = true }.padding(horizontal = 8.dp, vertical = 4.dp)) {
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
                                            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFF444444))) { Box(Modifier.fillMaxWidth(percentage / 100f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(when { percentage > 80 -> Color(0xFFFF5252); percentage > 50 -> Color(0xFFFFC107); else -> ZarpAccent })) }
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
                                Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFF5252)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Stop, "Stop", tint = Color.White, modifier = Modifier.size(14.dp)) }
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

                // Build Failed Notification
                AnimatedVisibility(visible = uiState.showBuildNotification && uiState.buildStatus?.isFailure == true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    BuildFailedCard(viewModel, uiState)
                }

                // Build Success Notification
                AnimatedVisibility(visible = uiState.showBuildNotification && uiState.buildStatus?.isSuccess == true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    BuildSuccessCard(viewModel)
                }

                // Voice Listening Card
                AnimatedVisibility(visible = uiState.voiceState.isListening, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    VoiceListeningCard(uiState.voiceState)
                }

                // Agent Progress
                AnimatedVisibility(visible = (uiState.isAgentMode || uiState.isAutomationMode) && uiState.agentProgress != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    AgentProgressCard(uiState, agentPanelExpanded) { agentPanelExpanded = !agentPanelExpanded }
                }

                // Automation Progress
                AnimatedVisibility(visible = uiState.isAutomationMode && uiState.automationProgress != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    AutomationProgressCard(uiState, autoPanelExpanded) { autoPanelExpanded = !autoPanelExpanded }
                }

                // Messages
                MessageList(
                    messages = uiState.messages, isAiThinking = uiState.isAiThinking, modifier = Modifier.weight(1f),
                    speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = { id, text -> viewModel.onSpeakMessage(id, text) },
                    likedMessages = uiState.likedMessages, dislikedMessages = uiState.dislikedMessages,
                    onLikeMessage = { viewModel.onLikeMessage(it) }, onDislikeMessage = { viewModel.onDislikeMessage(it) },
                    onRegenerate = { viewModel.onRegenerate() },
                    isSearchMode = uiState.isSearchMode,
                    searchProgress = if (uiState.isAiThinking && uiState.isSearchMode) 0.5f else 0f, searchEngines = emptyMap()
                )

                // Input Bar
                InputBar(
                    inputText = uiState.inputText, onInputChanged = { viewModel.onInputChanged(it) },
                    onSend = { viewModel.onSend() }, onMicTap = { viewModel.onStartVoiceInput(voiceLauncher) },
                    onAttachmentTap = { viewModel.onAttachmentTap() }, isListening = uiState.isListening,
                    isThinking = uiState.isAiThinking && !uiState.isPaused, isPaused = uiState.isPaused,
                    onPause = { viewModel.onPauseGeneration() }, onResume = { viewModel.onResumeGeneration() },
                    attachedImageUris = uiState.selectedImageUris, attachedFileNames = uiState.selectedFileNames,
                    attachedFileTypes = uiState.selectedFileTypes, onRemoveAttachment = { index -> viewModel.removeSingleAttachment(index) },
                    isTranslateMode = uiState.isTranslateMode, onToggleTranslateMode = { viewModel.onToggleTranslateMode() },
                    isSearchMode = uiState.isSearchMode, onToggleSearchMode = { viewModel.onToggleSearchMode() },
                    isAgentMode = uiState.isAgentMode, onToggleAgentMode = { viewModel.onToggleAgentMode() },
                    isAutomationMode = uiState.isAutomationMode, onToggleAutomationMode = { viewModel.onToggleAutomationMode() }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
// Build Failed Card
// ═══════════════════════════════════════════

@Composable
private fun BuildFailedCard(viewModel: ChatViewModel, uiState: ChatViewModel.ChatUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2D0D0D)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("❌", fontSize = 18.sp); Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Build Failed", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${uiState.buildLog?.errors?.size ?: 0} errors", color = Color(0xFFFF8A80), fontSize = 11.sp)
                }
                IconButton(onClick = { viewModel.onDismissBuildNotification() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Dismiss", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp)) }
            }
            uiState.buildLog?.errors?.take(3)?.forEach { error ->
                Spacer(modifier = Modifier.height(4.dp)); Text(error.take(120), color = Color(0xFFFFCDD2), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.onFixAndRebuild() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)), modifier = Modifier.fillMaxWidth(), enabled = !uiState.isFixingBuild) {
                if (uiState.isFixingBuild) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)); Text("Fixing...", color = Color.White) }
                else { Icon(Icons.Default.Build, "Fix", modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("🔧 Fix & Rebuild", color = Color.White) }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Build Success Card
// ═══════════════════════════════════════════

@Composable
private fun BuildSuccessCard(viewModel: ChatViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2D0D)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✅", fontSize = 18.sp); Spacer(modifier = Modifier.width(8.dp))
            Text("Build Passed!", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.onDismissBuildNotification() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Dismiss", tint = Color(0xFF00E676), modifier = Modifier.size(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════════
// Voice Listening Card
// ═══════════════════════════════════════════

@Composable
private fun VoiceListeningCard(voiceState: com.example.data.StreamingVoiceManager.VoiceState) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1F2D)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎤", fontSize = 18.sp); Spacer(modifier = Modifier.width(8.dp))
                Text(if (voiceState.partialText.isBlank()) "Listening..." else voiceState.partialText, color = Color(0xFF81D4FA), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (voiceState.error != null) { Spacer(modifier = Modifier.height(4.dp)); Text(voiceState.error, color = Color(0xFFFF5252), fontSize = 10.sp) }
        }
    }
}

// ═══════════════════════════════════════════
// Agent Progress Card
// ═══════════════════════════════════════════

@Composable
private fun AgentProgressCard(uiState: ChatViewModel.ChatUiState, expanded: Boolean, onToggle: () -> Unit) {
    uiState.agentProgress?.let { progress ->
        Card(modifier = Modifier.fillMaxWidth().padding(6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1F0D)), shape = RoundedCornerShape(10.dp)) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (uiState.isAgentMode) "🤖 Agent" else "🎮 Automation", color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${(progress.percentage * 100).toInt()}%", color = Color(0xFF00E676), fontSize = 11.sp); Spacer(modifier = Modifier.width(4.dp))
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Toggle", tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { progress.percentage }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = Color(0xFF00E676), trackColor = Color(0xFF1A3A1A))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(progress.stepDescription, color = Color(0xFFC8E6C9), fontSize = 12.sp)
                        Text(progress.message, color = Color(0xFF81C784), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Automation Progress Card
// ═══════════════════════════════════════════

@Composable
private fun AutomationProgressCard(uiState: ChatViewModel.ChatUiState, expanded: Boolean, onToggle: () -> Unit) {
    uiState.automationProgress?.let { progress ->
        Card(modifier = Modifier.fillMaxWidth().padding(6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0D2E)), shape = RoundedCornerShape(10.dp)) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
                    Text("🎮 Automation", color = Color(0xFFCE93D8), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${(progress.percentage * 100).toInt()}%", color = Color(0xFFCE93D8), fontSize = 11.sp); Spacer(modifier = Modifier.width(4.dp))
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Toggle", tint = Color(0xFFCE93D8), modifier = Modifier.size(16.dp))
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { progress.percentage }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = Color(0xFFCE93D8), trackColor = Color(0xFF2A1A3A))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Step ${progress.currentStep}/${progress.totalSteps}", color = Color(0xFFE1BEE7), fontSize = 12.sp)
                        Text(progress.description, color = Color(0xFFCE93D8), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
