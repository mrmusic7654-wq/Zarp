package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiRepository
import com.example.data.MockData
import com.example.model.Conversation
import com.example.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    data class ChatUiState(
        val messages: List<Message> = emptyList(),
        val inputText: String = "",
        val isListening: Boolean = false,
        val isAiThinking: Boolean = false,
        val currentConversationId: String? = null,
        val conversations: List<Conversation> = MockData.conversations,
        val isDrawerOpen: Boolean = false,
        val showAttachmentSheet: Boolean = false,
        val selectedImageUri: Uri? = null,
        val selectedFileName: String? = null,
        val selectedModel: String = "Gemini 2.5 Flash"
    )

    companion object {
        // ────────────────────────────────────────────
        // Free-tier models confirmed by Gemini API
        // ────────────────────────────────────────────
        val availableModels = listOf(
            "Gemini 3 Flash Preview",
            "Gemini 3.1 Flash Lite Preview",
            "Gemini 2.5 Flash",
            "Gemini 2.5 Flash-Lite",
            "Gemini 2.0 Flash",
            "Gemini 2.0 Flash-Lite",
            "Gemma 4 26B",
            "Gemma 4 31B"
        )

        fun getModelApiName(displayName: String): String = when (displayName) {
            "Gemini 3 Flash Preview"          -> "gemini-3-flash-preview"
            "Gemini 3.1 Flash Lite Preview"  -> "gemini-3.1-flash-lite-preview"
            "Gemini 2.5 Flash"                -> "gemini-2.5-flash"
            "Gemini 2.5 Flash-Lite"           -> "gemini-2.5-flash-lite"
            "Gemini 2.0 Flash"                -> "gemini-2.0-flash"
            "Gemini 2.0 Flash-Lite"           -> "gemini-2.0-flash-lite"
            "Gemma 4 26B"                     -> "gemma-4-26b-a4b-it"
            "Gemma 4 31B"                     -> "gemma-4-31b-it"
            else -> "gemini-2.5-flash"
        }
    }

    private val repository = GeminiRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // ────────────────────────────────────────────
    // Input handling
    // ────────────────────────────────────────────
    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    // ────────────────────────────────────────────
    // Send message (text only, or text + image)
    // ────────────────────────────────────────────
    fun onSend() {
        val currentText = _uiState.value.inputText
        val imageUri = _uiState.value.selectedImageUri

        if (currentText.isBlank() && imageUri == null) return

        val displayText = when {
            currentText.isNotBlank() && imageUri != null -> "$currentText\n📷 Image attached"
            currentText.isNotBlank() -> currentText
            imageUri != null -> "📷 Image attached"
            else -> return
        }

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            text = displayText,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            isAiThinking = true,
            selectedImageUri = null,
            selectedFileName = null
        )

        val modelName = getModelApiName(_uiState.value.selectedModel)
        Log.d("ChatViewModel", "Sending to model: $modelName")

        viewModelScope.launch {
            val responseText = if (imageUri != null) {
                repository.generateResponseWithImage(currentText, imageUri, modelName)
            } else {
                repository.generateResponse(currentText, modelName)
            }

            val aiMessage = Message(
                id = UUID.randomUUID().toString(),
                text = responseText,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMessage,
                isAiThinking = false
            )
        }
    }

    // ────────────────────────────────────────────
    // Attachment handling
    // ────────────────────────────────────────────
    fun onImageSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            selectedFileName = null
        )
    }

    fun onFileSelected(uri: Uri, fileName: String) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            selectedFileName = fileName
        )
    }

    fun clearImageSelection() {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = null,
            selectedFileName = null
        )
    }

    fun onAttachmentTap() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = true)
    }

    fun dismissAttachmentSheet() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = false)
    }

    // ────────────────────────────────────────────
    // Voice input (mock)
    // ────────────────────────────────────────────
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

    // ────────────────────────────────────────────
    // Conversation management
    // ────────────────────────────────────────────
    fun onNewChat() {
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            currentConversationId = null,
            isDrawerOpen = false,
            inputText = "",
            selectedImageUri = null,
            selectedFileName = null
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

    // ────────────────────────────────────────────
    // Drawer
    // ────────────────────────────────────────────
    fun onToggleDrawer(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen)
    }

    // ────────────────────────────────────────────
    // Model selection
    // ────────────────────────────────────────────
    fun onModelSelected(model: String) {
        Log.d("ChatViewModel", "Model selected: $model")
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }
}
