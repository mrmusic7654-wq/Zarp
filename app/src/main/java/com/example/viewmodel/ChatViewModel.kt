package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Conversation
import com.example.model.Message
import com.example.data.MockData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {
    
    data class ChatUiState(
        val messages: List<Message> = emptyList(),
        val inputText: String = "",
        val isListening: Boolean = false,
        val isAiThinking: Boolean = false,
        val currentConversationId: String? = null,
        val conversations: List<Conversation> = MockData.conversations,
        val isDrawerOpen: Boolean = false,
        val showAttachmentSheet: Boolean = false,
        val fileSelected: Boolean = false,
        val selectedModel: String = "Gemini 1.5 Flash"
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSend() {
        val currentText = _uiState.value.inputText
        if (currentText.isBlank()) return

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            text = currentText,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            isAiThinking = true,
            fileSelected = false
        )

        // Mock AI response
        viewModelScope.launch {
            delay(1500) // thinking delay
            val aiMessage = Message(
                id = UUID.randomUUID().toString(),
                text = "This is a mock response from Zarp. You said: \"$currentText\"",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMessage,
                isAiThinking = false
            )
        }
    }

    fun onMicTap() {
        _uiState.value = _uiState.value.copy(isListening = true, inputText = "")
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(
                isListening = false,
                inputText = "What's the weather like today?"
            )
        }
    }

    fun onNewChat() {
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            currentConversationId = null,
            isDrawerOpen = false,
            inputText = ""
        )
    }

    fun onSelectConversation(id: String) {
        val conversation = _uiState.value.conversations.find { it.id == id }
        if (conversation != null) {
            _uiState.value = _uiState.value.copy(
                messages = conversation.messages,
                currentConversationId = id,
                isDrawerOpen = false
            )
        }
    }

    fun onDeleteConversation(id: String) {
        _uiState.value = _uiState.value.copy(
            conversations = _uiState.value.conversations.filter { it.id != id }
        )
        if (_uiState.value.currentConversationId == id) {
            onNewChat()
        }
    }

    fun onToggleDrawer(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen)
    }

    fun onAttachmentTap() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = true)
    }

    fun dismissAttachmentSheet() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = false)
    }

    fun onFileSelected() {
        _uiState.value = _uiState.value.copy(
            showAttachmentSheet = false,
            fileSelected = true
        )
    }
    
    fun onRemoveFile() {
        _uiState.value = _uiState.value.copy(fileSelected = false)
    }

    fun onModelSelected(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }
}
