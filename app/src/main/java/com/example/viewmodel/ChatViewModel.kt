package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.data.GeminiRepository
import com.example.data.local.ChatDatabase
import com.example.model.Conversation
import com.example.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    data class ChatUiState(
        val messages: List<Message> = emptyList(),
        val inputText: String = "",
        val isListening: Boolean = false,
        val isAiThinking: Boolean = false,
        val currentConversationId: String? = null,
        val conversations: List<Conversation> = emptyList(),
        val isDrawerOpen: Boolean = false,
        val showAttachmentSheet: Boolean = false,
        val selectedImageUri: Uri? = null,
        val selectedFileName: String? = null,
        val selectedModel: String = "Gemini 2.5 Flash",
        val isDarkTheme: Boolean = true
    )

    companion object {
        val availableModels = listOf(
            "Gemini 3 Flash Preview",
            "Gemini 3.1 Flash Lite Preview",
            "Gemini 2.5 Flash",
            "Gemini 2.5 Flash-Lite",
            "Gemma 4 26B",
            "Gemma 4 31B"
        )

        fun getModelApiName(displayName: String): String = when (displayName) {
            "Gemini 3 Flash Preview"         -> "models/gemini-3-flash-preview"
            "Gemini 3.1 Flash Lite Preview" -> "models/gemini-3.1-flash-lite-preview"
            "Gemini 2.5 Flash"               -> "models/gemini-2.5-flash"
            "Gemini 2.5 Flash-Lite"          -> "models/gemini-2.5-flash-lite"
            "Gemma 4 26B"                    -> "models/gemma-4-26b-a4b-it"
            "Gemma 4 31B"                    -> "models/gemma-4-31b-it"
            else -> "models/gemini-2.5-flash"
        }
    }

    private val database = ChatDatabase.getInstance(application)
    private val chatRepository = ChatRepository(database)
    private val geminiRepository = GeminiRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Load conversations from DB
        viewModelScope.launch {
            chatRepository.getAllConversations().collect { conversations ->
                _uiState.value = _uiState.value.copy(conversations = conversations)
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSend() {
        val currentText = _uiState.value.inputText
        val imageUri = _uiState.value.selectedImageUri
        var conversationId = _uiState.value.currentConversationId

        if (currentText.isBlank() && imageUri == null) return

        val displayText = when {
            currentText.isNotBlank() && imageUri != null -> "$currentText\n📷 Image attached"
            currentText.isNotBlank() -> currentText
            imageUri != null -> "📷 Image attached"
            else -> return
        }

        // Optimistic UI update
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            text = displayText,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        val currentMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            inputText = "",
            isAiThinking = true,
            selectedImageUri = null,
            selectedFileName = null
        )

        viewModelScope.launch {
            try {
                // Create conversation if new
                if (conversationId == null) {
                    val newConv = chatRepository.createNewConversation(displayText)
                    conversationId = newConv.id
                    _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
                } else {
                    chatRepository.addMessageToConversation(conversationId, displayText, true)
                }

                val modelName = getModelApiName(_uiState.value.selectedModel)
                Log.d("ChatViewModel", "Calling Gemini with model: $modelName")

                // Call Gemini API
                val responseText = if (imageUri != null) {
                    geminiRepository.generateResponseWithImage(currentText, imageUri, modelName)
                } else {
                    geminiRepository.generateResponse(currentText, modelName)
                }

                Log.d("ChatViewModel", "Got response: ${responseText.take(50)}...")

                // Save AI message to DB
                chatRepository.addMessageToConversation(conversationId, responseText, false)

                // Update UI directly (not via Flow)
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
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in onSend", e)
                _uiState.value = _uiState.value.copy(
                    isAiThinking = false,
                    messages = _uiState.value.messages + Message(
                        id = UUID.randomUUID().toString(),
                        text = "❌ Error: ${e.localizedMessage ?: "Something went wrong"}",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun onImageSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, selectedFileName = null)
    }

    fun onFileSelected(uri: Uri, fileName: String) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, selectedFileName = fileName)
    }

    fun clearImageSelection() {
        _uiState.value = _uiState.value.copy(selectedImageUri = null, selectedFileName = null)
    }

    fun onAttachmentTap() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = true)
    }

    fun dismissAttachmentSheet() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = false)
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
            inputText = "",
            selectedImageUri = null,
            selectedFileName = null
        )
    }

    fun onSelectConversation(id: String) {
        _uiState.value = _uiState.value.copy(currentConversationId = id, isDrawerOpen = false)
        viewModelScope.launch {
            chatRepository.getMessagesForConversation(id).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    fun onDeleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) onNewChat()
        }
    }

    fun onToggleDrawer(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen)
    }

    fun onModelSelected(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun onToggleTheme() {
        _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme)
    }
}
