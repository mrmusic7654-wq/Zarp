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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
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

    // ── Voice input launcher ──
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) {
            viewModel.onVoiceResult(text)
        }
    }

    // ── API key warning ──
    LaunchedEffect(Unit) {
        if (KeyManager.getApiKey(context).isNullOrBlank()) {
            snackbarHostState.showSnackbar("API key missing. Set it in Settings → API Keys.")
        }
    }

    // ── Drawer sync ──
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

    // ── Attachment bottom sheet ──
    if (uiState.showAttachmentSheet) {
        AttachmentSheet(
            onDismiss = { viewModel.dismissAttachmentSheet() },
            onImageSelected = { uri -> viewModel.onImageSelected(uri) },
            onFileSelected = { uri, name -> viewModel.onFileSelected(uri, name) }
        )
    }

    // ── Custom style dialog ──
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
                                "e.g. Be brutally honest, roast me, no sugarcoating. Or: Act like a pirate, use pirate language.",
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
                                    text = uiState.selectedModel,
                                    color = ZarpTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Select Model",
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
                                    val percentage = if (limit > 0)
                                        (used * 100 / limit).coerceIn(0, 100) else 0
                                    val isSelected = uiState.selectedModel == model

                                    DropdownMenuItem(
                                        text = {
                                            Column(modifier = Modifier.width(220.dp)) {
                                                Text(
                                                    text = model,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isSelected) ZarpAccent
                                                            else ZarpTextPrimary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "$used / $limit requests today",
                                                    fontSize = 11.sp,
                                                    color = ZarpTextTertiary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .background(
                                                            Color(0xFF444444),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                ) {
                                                    Box(
                                                        modifier = Modifier
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
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = ZarpTextPrimary
                            )
                        }
                    },
                    actions = {
                        // ── Style button ──
                        IconButton(onClick = { viewModel.onShowStyleDialog() }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Response style",
                                tint = if (uiState.customStyle.isNotBlank()) ZarpAccent
                                       else ZarpTextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        // ── New chat ──
                        IconButton(onClick = { viewModel.onNewChat() }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "New chat",
                                tint = ZarpTextPrimary
                            )
                        }
                        // ── More options ──
                        IconButton(onClick = { /* Options */ }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // ── Messages ──
                MessageList(
                    messages = uiState.messages,
                    isAiThinking = uiState.isAiThinking,
                    modifier = Modifier.weight(1f)
                )

                // ── Input bar with attachment chip ──
                InputBar(
                    inputText = uiState.inputText,
                    onInputChanged = { viewModel.onInputChanged(it) },
                    onSend = { viewModel.onSend() },
                    onMicTap = { viewModel.onStartVoiceInput(voiceLauncher) },
                    onAttachmentTap = { viewModel.onAttachmentTap() },
                    isListening = uiState.isListening,
                    attachedImageUri = uiState.selectedImageUri,
                    attachedFileName = uiState.selectedFileName,
                    onRemoveAttachment = { viewModel.clearImageSelection() }
                )
            }
        }
    }
}
