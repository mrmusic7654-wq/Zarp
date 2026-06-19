package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiRepository
import com.example.data.UsageTracker
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
            "Gemini 2.5 Flash",
            "Gemini 2.5 Flash-Lite",
            "Gemini 3 Flash Preview",
            "Gemini 3.1 Flash Lite Preview",
            "Gemma 4 26B",
            "Gemma 4 31B"
        )

        fun getModelApiName(displayName: String): String = when (displayName) {
            "Gemini 2.5 Flash"               -> "models/gemini-2.5-flash"
            "Gemini 2.5 Flash-Lite"          -> "models/gemini-2.5-flash-lite"
            "Gemini 3 Flash Preview"         -> "models/gemini-3-flash-preview"
            "Gemini 3.1 Flash Lite Preview" -> "models/gemini-3.1-flash-lite-preview"
            "Gemma 4 26B"                    -> "models/gemma-4-26b-a4b-it"
            "Gemma 4 31B"                    -> "models/gemma-4-31b-it"
            else -> "models/gemini-2.5-flash"
        }
    }

    private val geminiRepository = GeminiRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // ──────────────────────────────────────────────
    // In‑memory conversation storage
    // ──────────────────────────────────────────────
    private val allConversations = mutableListOf<Conversation>()

    // ──────────────────────────────────────────────
    // Input
    // ──────────────────────────────────────────────
    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    // ──────────────────────────────────────────────
    // Send message (text / image)
    // ──────────────────────────────────────────────
    fun onSend() {
        val currentText = _uiState.value.inputText
        val imageUri = _uiState.value.selectedImageUri
        if (currentText.isBlank() && imageUri == null) return

        val displayText = currentText.ifBlank { "📷 Image" }
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

        viewModelScope.launch {
            try {
                val modelName = getModelApiName(_uiState.value.selectedModel)
                Log.d("ChatViewModel", "Calling Gemini: $modelName")

                val responseText = if (imageUri != null) {
                    geminiRepository.generateResponseWithImage(currentText, imageUri, modelName)
                } else {
                    geminiRepository.generateResponse(currentText, modelName)
                }

                Log.d("ChatViewModel", "Response: ${responseText.take(60)}...")

                // Record today's usage
                UsageTracker.recordRequest(getApplication(), _uiState.value.selectedModel)

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

                saveCurrentConversation()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "onSend failed", e)
                _uiState.value = _uiState.value.copy(isAiThinking = false)
                _uiState.value = _uiState.value.copy(
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

    // ──────────────────────────────────────────────
    // Conversation persistence (in‑memory)
    // ──────────────────────────────────────────────
    private fun saveCurrentConversation() {
        val msgs = _uiState.value.messages
        if (msgs.isEmpty()) return

        val id = _uiState.value.currentConversationId ?: UUID.randomUUID().toString()
        val title = msgs.firstOrNull()?.text?.take(40) ?: "New chat"
        val existing = allConversations.indexOfFirst { it.id == id }
        val conv = Conversation(
            id = id,
            title = title,
            dateGroup = "Today",
            messages = msgs
        )
        if (existing >= 0) allConversations[existing] = conv
        else allConversations.add(0, conv)

        _uiState.value = _uiState.value.copy(
            currentConversationId = id,
            conversations = allConversations.toList()
        )
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
        val conv = allConversations.find { it.id == id } ?: return
        _uiState.value = _uiState.value.copy(
            messages = conv.messages,
            currentConversationId = id,
            isDrawerOpen = false
        )
    }

    fun onDeleteConversation(id: String) {
        allConversations.removeAll { it.id == id }
        if (_uiState.value.currentConversationId == id) onNewChat()
        _uiState.value = _uiState.value.copy(conversations = allConversations.toList())
    }

    // ──────────────────────────────────────────────
    // Attachments
    // ──────────────────────────────────────────────
    fun onAttachmentTap() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = true)
    }

    fun dismissAttachmentSheet() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = false)
    }

    fun onImageSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, selectedFileName = null)
    }

    fun onFileSelected(uri: Uri, name: String) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, selectedFileName = name)
    }

    fun clearImageSelection() {
        _uiState.value = _uiState.value.copy(selectedImageUri = null, selectedFileName = null)
    }

    // ──────────────────────────────────────────────
    // Voice mock
    // ──────────────────────────────────────────────
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

    // ──────────────────────────────────────────────
    // UI toggles
    // ──────────────────────────────────────────────
    fun onToggleDrawer(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen)
    }

    fun onModelSelected(model: String) {
        Log.d("ChatViewModel", "Model selected: $model")
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun onToggleTheme() {
        _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme)
    }
}
