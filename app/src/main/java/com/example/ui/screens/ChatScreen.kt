package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.KeyManager
import com.example.data.UsageTracker
import com.example.ui.components.AttachmentSheet
import com.example.ui.components.InputBar
import com.example.ui.components.MessageList
import com.example.ui.components.SidebarDrawer
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpBubbleBg
import com.example.ui.theme.ZarpMainBg
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary
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

    // Warn if API key missing
    LaunchedEffect(Unit) {
        if (KeyManager.getApiKey(context).isNullOrBlank()) {
            snackbarHostState.showSnackbar("API key missing. Set it in Settings → API Key.")
        }
    }

    // Sync drawer state
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.isDrawerOpen && drawerState.isOpen) drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isDrawerOpen) viewModel.onToggleDrawer(drawerState.isOpen)
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

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
                                    modifier = Modifier.padding(start = 4.dp).size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showModelSelector,
                                onDismissRequest = { showModelSelector = false },
                                modifier = Modifier.background(ZarpBubbleBg)
                            ) {
                                ChatViewModel.availableModels.forEach { model ->
                                    val used = UsageTracker.getCount(context, model)
                                    val limit = UsageTracker.getLimit(model)
                                    val percentage = if (limit > 0) (used * 100 / limit).coerceIn(0, 100) else 0
                                    val isSelected = uiState.selectedModel == model

                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model,
                                                    color = if (isSelected) ZarpAccent else ZarpTextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "$used / $limit requests today",
                                                    color = ZarpTextTertiary,
                                                    fontSize = 11.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .background(Color(0xFF333333), RoundedCornerShape(2.dp))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(percentage / 100f)
                                                            .height(3.dp)
                                                            .background(ZarpAccent, RoundedCornerShape(2.dp))
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
                        IconButton(onClick = { viewModel.onNewChat() }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "New chat",
                                tint = ZarpTextPrimary
                            )
                        }
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
                // Image preview
                val uri = uiState.selectedImageUri
                if (uri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ZarpBubbleBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Attached image",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.selectedFileName ?: "Image attached",
                            color = ZarpTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearImageSelection() }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = ZarpTextPrimary
                            )
                        }
                    }
                }

                MessageList(
                    messages = uiState.messages,
                    isAiThinking = uiState.isAiThinking,
                    modifier = Modifier.weight(1f)
                )

                InputBar(
                    inputText = uiState.inputText,
                    onInputChanged = { viewModel.onInputChanged(it) },
                    onSend = { viewModel.onSend() },
                    onMicTap = { viewModel.onMicTap() },
                    onAttachmentTap = { viewModel.onAttachmentTap() },
                    isListening = uiState.isListening
                )
            }
        }

        if (uiState.showAttachmentSheet) {
            AttachmentSheet(
                onDismiss = { viewModel.dismissAttachmentSheet() },
                onImageSelected = { uri -> viewModel.onImageSelected(uri) },
                onFileSelected = { uri, name -> viewModel.onFileSelected(uri, name) }
            )
        }
    }
}
