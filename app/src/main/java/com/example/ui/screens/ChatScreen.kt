package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AttachmentSheet
import com.example.ui.components.InputBar
import com.example.ui.components.MessageList
import com.example.ui.components.SidebarDrawer
import com.example.ui.theme.ZarpMainBg
import com.example.ui.theme.ZarpTextPrimary
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
    var showModelSelector by androidx.compose.runtime.mutableStateOf(false)

    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen && drawerState.isClosed) {
            drawerState.open()
        } else if (!uiState.isDrawerOpen && drawerState.isOpen) {
            drawerState.close()
        }
    }
    
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isDrawerOpen) {
            viewModel.onToggleDrawer(drawerState.isOpen)
        }
    }

    LaunchedEffect(uiState.fileSelected) {
        if (uiState.fileSelected) {
            snackbarHostState.showSnackbar("File selected")
            viewModel.onRemoveFile()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
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
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Select Model",
                                    tint = ZarpTextPrimary,
                                    modifier = Modifier.padding(start = 4.dp).size(20.dp)
                                )
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = showModelSelector,
                                onDismissRequest = { showModelSelector = false },
                                modifier = Modifier.background(com.example.ui.theme.ZarpBubbleBg)
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Gemini 1.5 Flash", color = ZarpTextPrimary) },
                                    onClick = { 
                                        viewModel.onModelSelected("Gemini 1.5 Flash")
                                        showModelSelector = false
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Gemini 1.5 Pro", color = ZarpTextPrimary) },
                                    onClick = { 
                                        viewModel.onModelSelected("Gemini 1.5 Pro")
                                        showModelSelector = false
                                    }
                                )
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
                        IconButton(onClick = { /* mock dropdown */ }) {
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
                onFileSelected = { viewModel.onFileSelected() }
            )
        }
    }
}
