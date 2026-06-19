package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.data.GeminiRepository
import com.example.data.UsageTracker
import com.example.data.local.ChatDatabase
import com.example.model.Conversation
import com.example.model.Message
import kotlinx.coroutines.Job
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
        val customStyle: String = "",
        val showStyleDialog: Boolean = false,
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

    private val database = ChatDatabase.getInstance(application)
    private val chatRepository = ChatRepository(database)
    private val geminiRepository = GeminiRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Track current generation job for stop button
    private var currentGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            chatRepository.getAllConversations().collect { conversations ->
                _uiState.value = _uiState.value.copy(conversations = conversations)
            }
        }
    }

    // ──────────────────────────────────────────────
    // Input
    // ──────────────────────────────────────────────
    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    // ──────────────────────────────────────────────
    // Send message
    // ──────────────────────────────────────────────
    fun onSend() {
        val currentText = _uiState.value.inputText
        val imageUri = _uiState.value.selectedImageUri
        var conversationId = _uiState.value.currentConversationId

        if (currentText.isBlank() && imageUri == null) return

        val displayText = currentText.ifBlank { "📷 Image" }
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

        currentGenerationJob = viewModelScope.launch {
            try {
                // Save user message to DB
                if (conversationId == null) {
                    val newConv = chatRepository.createNewConversation(displayText)
                    conversationId = newConv.id
                    _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
                } else {
                    chatRepository.addMessageToConversation(conversationId, displayText, true)
                }

                // Build history (exclude the last user message)
                val history = currentMessages.dropLast(1)

                // Call Gemini with full conversation context
                val modelName = getModelApiName(_uiState.value.selectedModel)
                val responseText = if (imageUri != null) {
                    geminiRepository.generateResponseWithImage(
                        prompt = currentText,
                        imageUri = imageUri,
                        modelName = modelName,
                        chatHistory = history,
                        customStyle = _uiState.value.customStyle
                    )
                } else {
                    geminiRepository.generateResponse(
                        prompt = currentText,
                        modelName = modelName,
                        chatHistory = history,
                        customStyle = _uiState.value.customStyle
                    )
                }

                // Record usage
                UsageTracker.recordRequest(getApplication(), _uiState.value.selectedModel)

                // Save AI response to DB
                chatRepository.addMessageToConversation(conversationId, responseText, false)

                // Update UI
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled by stop button — add partial message
                _uiState.value = _uiState.value.copy(
                    isAiThinking = false,
                    messages = _uiState.value.messages + Message(
                        id = UUID.randomUUID().toString(),
                        text = "⏹️ Generation stopped.",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
                throw e
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
    // Stop generation
    // ──────────────────────────────────────────────
    fun onStopGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
    }

    // ──────────────────────────────────────────────
    // Conversation management
    // ──────────────────────────────────────────────
    fun onNewChat() {
        currentGenerationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            currentConversationId = null,
            isDrawerOpen = false,
            inputText = "",
            selectedImageUri = null,
            selectedFileName = null,
            isAiThinking = false
        )
    }

    fun onSelectConversation(id: String) {
        currentGenerationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            currentConversationId = id,
            isDrawerOpen = false,
            isAiThinking = false
        )
        viewModelScope.launch {
            chatRepository.getMessagesForConversation(id).first().let { messages ->
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
    // Voice input
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

    fun onStartVoiceInput(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        try {
            _uiState.value = _uiState.value.copy(isListening = true)
            launcher.launch(intent)
        } catch (e: Exception) {
            onMicTap()
        }
    }

    fun onVoiceResult(text: String) {
        _uiState.value = _uiState.value.copy(
            inputText = text,
            isListening = false
        )
    }

    // ──────────────────────────────────────────────
    // UI toggles
    // ──────────────────────────────────────────────
    fun onToggleDrawer(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen)
    }

    fun onModelSelected(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun onCustomStyleChanged(style: String) {
        _uiState.value = _uiState.value.copy(customStyle = style)
    }

    fun onShowStyleDialog() {
        _uiState.value = _uiState.value.copy(showStyleDialog = true)
    }

    fun onDismissStyleDialog() {
        _uiState.value = _uiState.value.copy(showStyleDialog = false)
    }

    fun onToggleTheme() {
        _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme)
    }
}
