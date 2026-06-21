package com.example.ui.screens

import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    var showMoreMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Voice launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.onVoiceResult(text)
        else viewModel.onCancelVoice()
    }

    // API key check
    LaunchedEffect(Unit) {
        if (KeyManager.getApiKey(context).isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = "🔑 API key missing — Set it in Settings",
                actionLabel = "Go",
                duration = SnackbarDuration.Long
            )
        }
    }

    // Drawer sync with animation
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.isDrawerOpen && drawerState.isOpen) drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isDrawerOpen) viewModel.onToggleDrawer(drawerState.isOpen)
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    // ── Sheets & Dialogs ────────────────────
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
        StyleDialog(
            currentStyle = uiState.customStyle,
            onApply = { viewModel.onCustomStyleChanged(it); viewModel.onDismissStyleDialog() },
            onReset = { viewModel.onCustomStyleChanged(""); viewModel.onDismissStyleDialog() },
            onDismiss = { viewModel.onDismissStyleDialog() }
        )
    }

    if (uiState.showTranslateDialog && uiState.translateResult != null) {
        TranslateResultDialog(
            result = uiState.translateResult ?: "",
            onDismiss = { viewModel.onDismissTranslateDialog() }
        )
    }

    // ── More Options Dropdown ──────────────
    if (showMoreMenu) {
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("📋 Export Chat", color = ZarpTextPrimary) },
                onClick = { showMoreMenu = false }
            )
            DropdownMenuItem(
                text = { Text("🗑️ Clear History", color = ZarpTextPrimary) },
                onClick = { showMoreMenu = false; viewModel.onNewChat() }
            )
            DropdownMenuItem(
                text = { Text("⚙️ Settings", color = ZarpTextPrimary) },
                onClick = { showMoreMenu = false; onNavigateToSettings() }
            )
        }
    }

    // ── Main Layout ─────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            Box(modifier = Modifier.width(drawerWidth)) {
                SidebarDrawer(
                    conversations = uiState.conversations,
                    currentConversationId = uiState.currentConversationId,
                    onNewChat = { viewModel.onNewChat(); scope.launch { drawerState.close() } },
                    onSelectConversation = { viewModel.onSelectConversation(it); scope.launch { drawerState.close() } },
                    onDeleteConversation = { viewModel.onDeleteConversation(it) },
                    onSettingsTap = { scope.launch { drawerState.close() }; onNavigateToSettings() }
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
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        // ── Model Selector ──
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showModelSelector) ZarpBubbleBg else Color.Transparent)
                                    .clickable { showModelSelector = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    uiState.selectedModel,
                                    color = ZarpTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ExpandMore,
                                    "Models",
                                    tint = ZarpAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showModelSelector,
                                onDismissRequest = { showModelSelector = false },
                                modifier = Modifier.width(240.dp)
                            ) {
                                Text(
                                    "Select Model",
                                    color = ZarpTextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                HorizontalDivider(color = ZarpDivider)

                                ChatViewModel.availableModels.forEach { model ->
                                    val used = UsageTracker.getCount(context, model)
                                    val limit = UsageTracker.getLimit(model)
                                    val percentage = if (limit > 0) (used * 100 / limit).coerceIn(0, 100) else 0
                                    val isSelected = uiState.selectedModel == model

                                    DropdownMenuItem(
                                        text = {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, null, tint = ZarpAccent, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        model,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) ZarpAccent else ZarpTextPrimary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "$used / $limit",
                                                        fontSize = 10.sp,
                                                        color = ZarpTextTertiary
                                                    )
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Box(
                                                        modifier = Modifier
                                                            .width(60.dp)
                                                            .height(3.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(Color(0xFF333333))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(percentage / 100f)
                                                                .height(3.dp)
                                                                .clip(RoundedCornerShape(2.dp))
                                                                .background(
                                                                    when {
                                                                        percentage > 80 -> Color(0xFFFF5252)
                                                                        percentage > 50 -> Color(0xFFFFC107)
                                                                        else -> ZarpAccent
                                                                    }
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.onModelSelected(model)
                                            showModelSelector = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "History", tint = ZarpTextPrimary)
                        }
                    },
                    actions = {
                        // Stop button — animated
                        AnimatedVisibility(
                            visible = uiState.isAiThinking || uiState.isPaused,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            IconButton(onClick = { viewModel.onStopGeneration() }) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFF5252)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Stop, "Stop", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Style button — glows when custom style is active
                        IconButton(onClick = { viewModel.onShowStyleDialog() }) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                "Style",
                                tint = if (uiState.customStyle.isNotBlank()) ZarpAccent else ZarpTextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // New chat
                        IconButton(onClick = { viewModel.onNewChat() }) {
                            Icon(Icons.Outlined.Edit, "New chat", tint = ZarpTextPrimary, modifier = Modifier.size(20.dp))
                        }

                        // More menu
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More", tint = ZarpTextTertiary, modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ZarpMainBg,
                        scrolledContainerColor = ZarpMainBg,
                        titleContentColor = ZarpTextPrimary
                    )
                )
            },
            containerColor = ZarpMainBg,
            bottomBar = {
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
                    onToggleSearchMode = { viewModel.onToggleSearchMode() }
                )
            }
        ) { paddingValues ->
            // ── Message List ──
            MessageList(
                messages = uiState.messages,
                isAiThinking = uiState.isAiThinking,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                speakingMessageId = uiState.speakingMessageId,
                onSpeakMessage = { id, text -> viewModel.onSpeakMessage(id, text) },
                likedMessages = uiState.likedMessages,
                dislikedMessages = uiState.dislikedMessages,
                onLikeMessage = { viewModel.onLikeMessage(it) },
                onDislikeMessage = { viewModel.onDislikeMessage(it) },
                onRegenerate = { viewModel.onRegenerate() }
            )
        }
    }
}

// ──────────────────────────────────────────────
// Style Dialog
// ──────────────────────────────────────────────
@Composable
private fun StyleDialog(
    currentStyle: String,
    onApply: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var styleText by remember { mutableStateOf(currentStyle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = ZarpAccent, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Custom Response Style", color = ZarpTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                Text(
                    "Tell Zarp how to behave in every response:",
                    color = ZarpTextTertiary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = styleText,
                    onValueChange = { styleText = it },
                    placeholder = {
                        Text(
                            "e.g. Be brutally honest, use dark humor, roast me gently. No sugarcoating.",
                            color = ZarpTextTertiary,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(130.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ZarpTextPrimary,
                        unfocusedTextColor = ZarpTextPrimary,
                        focusedBorderColor = ZarpAccent,
                        unfocusedBorderColor = ZarpInputBorder,
                        cursorColor = ZarpAccent
                    ),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (currentStyle.isNotBlank()) {
                    TextButton(onClick = onReset) {
                        Icon(Icons.Outlined.Refresh, null, tint = ZarpTextTertiary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset to default", color = ZarpTextTertiary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(styleText) },
                colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("✅ Apply", color = Color.White, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ZarpTextTertiary)
            }
        },
        containerColor = ZarpSidebarBg,
        shape = RoundedCornerShape(20.dp)
    )
}

// ──────────────────────────────────────────────
// Translate Result Dialog
// ──────────────────────────────────────────────
@Composable
private fun TranslateResultDialog(
    result: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌐", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Translation", color = ZarpTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            SelectionContainer {
                Text(
                    result,
                    color = ZarpTextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("OK", color = Color.White)
            }
        },
        containerColor = ZarpSidebarBg,
        shape = RoundedCornerShape(20.dp)
    )
}
