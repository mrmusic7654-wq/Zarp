package com.example.ui.screens

import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current

    // ── Voice input launcher ─────────────────
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.onVoiceResult(text)
    }

    // ── API key warning ─────────────────────
    LaunchedEffect(Unit) {
        if (KeyManager.getApiKey(context).isNullOrBlank()) {
            snackbarHostState.showSnackbar("API key missing. Set it in Settings → API Keys.")
        }
    }

    // ── Drawer sync ─────────────────────────
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.isDrawerOpen && drawerState.isOpen) drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isDrawerOpen)
            viewModel.onToggleDrawer(drawerState.isOpen)
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // ── Attachment sheet ────────────────────
    if (uiState.showAttachmentSheet) {
        AttachmentSheet(
            onDismiss = { viewModel.dismissAttachmentSheet() },
            onImageSelected = { uri -> viewModel.onImageSelected(uri) },
            onImagesSelected = { uris -> viewModel.onImagesSelected(uris) },
            onFileSelected = { uri, name -> viewModel.onFileSelected(uri, name) },
            onFilesSelected = { uris -> viewModel.onFilesSelected(uris) }
        )
    }

    // ── Custom style dialog ─────────────────
    if (uiState.showStyleDialog) {
        var styleText by remember { mutableStateOf(uiState.customStyle) }

        AlertDialog(
            onDismissRequest = { viewModel.onDismissStyleDialog() },
            title = {
                Text("🎭 Custom Response Style", color = ZarpTextPrimary, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        "Describe how you want Zarp to behave:",
                        color = ZarpTextTertiary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = styleText,
                        onValueChange = { styleText = it },
                        placeholder = {
                            Text(
                                "e.g. Be brutally honest, roast me.",
                                color = ZarpTextTertiary,
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
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
                    TextButton(
                        onClick = {
                            viewModel.onCustomStyleChanged("")
                            viewModel.onDismissStyleDialog()
                        }
                    ) {
                        Text("🔄 Reset to default", color = ZarpTextTertiary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onCustomStyleChanged(styleText)
                        viewModel.onDismissStyleDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent)
                ) {
                    Text("✅ Apply", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissStyleDialog() }) {
                    Text("Cancel", color = ZarpTextTertiary)
                }
            },
            containerColor = ZarpSidebarBg
        )
    }

    // ── Translate result dialog ─────────────
    if (uiState.showTranslateDialog && uiState.translateResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissTranslateDialog() },
            title = {
                Text("🌐 Translation", color = ZarpTextPrimary, fontSize = 18.sp)
            },
            text = {
                SelectionContainer {
                    Text(
                        uiState.translateResult ?: "",
                        color = ZarpTextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onDismissTranslateDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = ZarpAccent)
                ) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = ZarpSidebarBg
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        drawerContent = {
            Box(modifier = Modifier.width(drawerWidth)) {
                SidebarDrawer(
                    conversations = uiState.conversations,
                    currentConversationId = uiState.currentConversationId,
                    onNewChat = {
                        viewModel.onNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = {
                        viewModel.onSelectConversation(it)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { viewModel.onDeleteConversation(it) },
                    onSettingsTap = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    }
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
                                modifier = Modifier
                                    .clickable { showModelSelector = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    uiState.selectedModel,
                                    color = ZarpTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    Icons.Default.ExpandMore,
                                    "Select Model",
                                    tint = ZarpTextPrimary,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showModelSelector,
                                onDismissRequest = { showModelSelector = false }
                            ) {
                                ChatViewModel.availableModels.forEach { model ->
                                    val used = UsageTracker.getCount(context, model)
                                    val limit = UsageTracker.getLimit(model)
                                    val percentage =
                                        if (limit > 0) (used * 100 / limit).coerceIn(0, 100) else 0
                                    val isSelected = uiState.selectedModel == model

                                    DropdownMenuItem(
                                        text = {
                                            Column(modifier = Modifier.width(220.dp)) {
                                                Text(
                                                    model,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isSelected) ZarpAccent
                                                            else ZarpTextPrimary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    "$used / $limit requests today",
                                                    fontSize = 11.sp,
                                                    color = ZarpTextTertiary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .background(
                                                            Color(0xFF444444),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                ) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxWidth(percentage / 100f)
                                                            .height(3.dp)
                                                            .background(
                                                                ZarpAccent,
                                                                RoundedCornerShape(2.dp)
                                                            )
                                                    )
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
                            Icon(Icons.Default.Menu, "Menu", tint = ZarpTextPrimary)
                        }
                    },
                    actions = {
                        // Stop button (visible when thinking or paused)
                        if (uiState.isAiThinking || uiState.isPaused) {
                            IconButton(onClick = { viewModel.onStopGeneration() }) {
                                Icon(
                                    Icons.Default.Stop,
                                    "Stop generation",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        // Style button
                        IconButton(onClick = { viewModel.onShowStyleDialog() }) {
                            Icon(
                                Icons.Default.Tune,
                                "Response style",
                                tint = if (uiState.customStyle.isNotBlank()) ZarpAccent
                                       else ZarpTextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        // New chat button
                        IconButton(onClick = { viewModel.onNewChat() }) {
                            Icon(
                                Icons.Outlined.Edit,
                                "New chat",
                                tint = ZarpTextPrimary
                            )
                        }
                        // More options
                        IconButton(onClick = { }) {
                            Icon(
                                Icons.Default.MoreVert,
                                "Options",
                                tint = ZarpTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ZarpMainBg,
                        scrolledContainerColor = ZarpMainBg
                    )
                )
            },
            containerColor = ZarpMainBg
        ) { paddingValues ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // ── Messages ──
                MessageList(
                    messages = uiState.messages,
                    isAiThinking = uiState.isAiThinking,
                    modifier = Modifier.weight(1f),
                    speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = { id, text -> viewModel.onSpeakMessage(id, text) }
                )

                // ── Input bar ──
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
                    onRemoveAttachment = { index -> viewModel.removeSingleAttachment(index) },
                    isTranslateMode = uiState.isTranslateMode,
                    onToggleTranslateMode = { viewModel.onToggleTranslateMode() }
                )
            }
        }
    }
}
