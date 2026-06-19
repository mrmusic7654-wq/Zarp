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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    data class ChatUiState(
        val messages: List<Message> = emptyList(),
        val inputText: String = "",
        val isListening: Boolean = false,
        val isAiThinking: Boolean = false,
        val isPaused: Boolean = false,
        val currentConversationId: String? = null,
        val conversations: List<Conversation> = emptyList(),
        val isDrawerOpen: Boolean = false,
        val showAttachmentSheet: Boolean = false,
        val selectedImageUris: List<Uri> = emptyList(),
        val selectedFileNames: List<String> = emptyList(),
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
            "Gemini 3.1 Flash Lite Preview"  -> "models/gemini-3.1-flash-lite-preview"
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

    private var currentGenerationJob: Job? = null
    private var lastPrompt: String = ""
    private var lastImageUris: List<Uri> = emptyList()

    init {
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
        val currentText = _uiState.value.inputText.ifBlank { lastPrompt }
        val imageUris = _uiState.value.selectedImageUris.ifEmpty { lastImageUris }
        var conversationId = _uiState.value.currentConversationId

        if (currentText.isBlank() && imageUris.isEmpty()) return

        lastPrompt = currentText
        lastImageUris = imageUris

        val displayText = when {
            currentText.isNotBlank() && imageUris.isNotEmpty() -> "$currentText\n📷 ${imageUris.size} image(s) attached"
            currentText.isNotBlank() -> currentText
            imageUris.isNotEmpty() -> "📷 ${imageUris.size} image(s) attached"
            else -> return
        }

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
            isPaused = false,
            selectedImageUris = emptyList(),
            selectedFileNames = emptyList()
        )

        currentGenerationJob = viewModelScope.launch {
            try {
                if (conversationId == null) {
                    val newConv = chatRepository.createNewConversation(displayText)
                    conversationId = newConv.id
                    _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
                } else {
                    chatRepository.addMessageToConversation(conversationId, displayText, true)
                }

                val history = currentMessages.dropLast(1)
                val modelName = getModelApiName(_uiState.value.selectedModel)

                val responseText = if (imageUris.isNotEmpty()) {
                    val firstImage = imageUris.first()
                    val remainingImages = imageUris.drop(1)
                    geminiRepository.generateResponseWithImage(
                        currentText, firstImage, modelName, history, _uiState.value.customStyle
                    )
                } else {
                    geminiRepository.generateResponse(
                        currentText, modelName, history, _uiState.value.customStyle
                    )
                }

                UsageTracker.recordRequest(getApplication(), _uiState.value.selectedModel)
                chatRepository.addMessageToConversation(conversationId, responseText, false)

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
                lastPrompt = ""
                lastImageUris = emptyList()
            } catch (e: CancellationException) {
                if (!_uiState.value.isPaused) {
                    _uiState.value = _uiState.value.copy(
                        isAiThinking = false,
                        messages = _uiState.value.messages + Message(
                            id = UUID.randomUUID().toString(),
                            text = "⏹️ Generation stopped.",
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e("ChatViewModel", "onSend failed", e)
                _uiState.value = _uiState.value.copy(isAiThinking = false)
            }
        }
    }

    fun onPauseGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        _uiState.value = _uiState.value.copy(isPaused = true, isAiThinking = false)
    }

    fun onResumeGeneration() {
        if (_uiState.value.isPaused) {
            _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = true)
            onSend()
        }
    }

    fun onStopGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = false)
        lastPrompt = ""
        lastImageUris = emptyList()
    }

    fun onVoiceResult(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, isListening = false)
        if (text.isNotBlank()) onSend()
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

    fun onNewChat() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        lastPrompt = ""
        lastImageUris = emptyList()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            currentConversationId = null,
            isDrawerOpen = false,
            inputText = "",
            selectedImageUris = emptyList(),
            selectedFileNames = emptyList(),
            isAiThinking = false,
            isPaused = false
        )
    }

    fun onSelectConversation(id: String) {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        lastPrompt = ""
        lastImageUris = emptyList()
        _uiState.value = _uiState.value.copy(
            currentConversationId = id,
            isDrawerOpen = false,
            isAiThinking = false,
            isPaused = false,
            messages = emptyList()
        )
        viewModelScope.launch {
            chatRepository.getMessagesForConversation(id).first().let { msgs ->
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
        }
    }

    fun onDeleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) onNewChat()
        }
    }

    fun onAttachmentTap() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = true)
    }

    fun dismissAttachmentSheet() {
        _uiState.value = _uiState.value.copy(showAttachmentSheet = false)
    }

    fun onImageSelected(uri: Uri) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        currentUris.add(uri)
        currentNames.add("Image ${currentUris.size}")
        _uiState.value = _uiState.value.copy(
            selectedImageUris = currentUris,
            selectedFileNames = currentNames
        )
    }

    fun onFileSelected(uri: Uri, name: String) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        currentUris.add(uri)
        currentNames.add(name)
        _uiState.value = _uiState.value.copy(
            selectedImageUris = currentUris,
            selectedFileNames = currentNames
        )
    }

    fun removeSingleAttachment(index: Int) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        if (index in currentUris.indices) {
            currentUris.removeAt(index)
            currentNames.removeAt(index)
        }
        _uiState.value = _uiState.value.copy(
            selectedImageUris = currentUris,
            selectedFileNames = currentNames
        )
    }

    fun clearImageSelection() {
        _uiState.value = _uiState.value.copy(
            selectedImageUris = emptyList(),
            selectedFileNames = emptyList()
        )
    }

    fun onMicTap() {
        _uiState.value = _uiState.value.copy(isListening = true, inputText = "")
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(
                isListening = false,
                inputText = "What's the weather today?"
            )
        }
    }

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
