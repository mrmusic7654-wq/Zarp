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
        val isDarkTheme: Boolean = true,
        val isSpeaking: Boolean = false,
        val speakingMessageId: String? = null,
        val isTranslateMode: Boolean = false,
        val translateLanguage: String = "English",
        val isTranslating: Boolean = false,
        val translateResult: String? = null,
        val showTranslateDialog: Boolean = false,
        val likedMessages: Set<String> = emptySet(),
        val dislikedMessages: Set<String> = emptySet()
    )

    companion object {
        val availableModels = listOf(
            "Gemini 3.5 Flash",
            "Gemini 3 Flash Preview",
            "Gemini 3.1 Flash-Lite",
            "Gemini 2.5 Pro",
            "Gemini 2.5 Flash",
            "Gemini 2.5 Flash-Lite",
            "Gemini 2.5 Flash-Lite Preview",
            "Gemma 4 26B",
            "Gemma 4 31B"
        )

        fun getModelApiName(displayName: String): String = when (displayName) {
            "Gemini 3.5 Flash"               -> "models/gemini-3.5-flash"
            "Gemini 3 Flash Preview"         -> "models/gemini-3-flash-preview"
            "Gemini 3.1 Flash-Lite"          -> "models/gemini-3.1-flash-lite"
            "Gemini 2.5 Pro"                 -> "models/gemini-2.5-pro"
            "Gemini 2.5 Flash"               -> "models/gemini-2.5-flash"
            "Gemini 2.5 Flash-Lite"          -> "models/gemini-2.5-flash-lite"
            "Gemini 2.5 Flash-Lite Preview"  -> "models/gemini-2.5-flash-lite-preview-09-2025"
            "Gemma 4 26B"                    -> "models/gemma-4-26b-a4b-it"
            "Gemma 4 31B"                    -> "models/gemma-4-31b-it"
            else -> "models/gemini-2.5-flash"
        }
    }

    private val database = ChatDatabase.getInstance(application)
    private val chatRepository = ChatRepository(database)
    private val geminiRepository = GeminiRepository(application)
    private val androidTTS = AndroidTTSManager(application)

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

    fun onToggleTranslateMode() {
        _uiState.value = _uiState.value.copy(isTranslateMode = !_uiState.value.isTranslateMode)
    }

    fun onSend() {
        var currentText = _uiState.value.inputText.ifBlank { lastPrompt }
        val imageUris = _uiState.value.selectedImageUris.ifEmpty { lastImageUris }
        var conversationId = _uiState.value.currentConversationId
        val isTranslateMode = _uiState.value.isTranslateMode

        if (currentText.isBlank() && imageUris.isEmpty()) return

        lastPrompt = currentText
        lastImageUris = imageUris

        val displayText = when {
            currentText.isNotBlank() && imageUris.isNotEmpty() -> "$currentText\n📎 ${imageUris.size} file(s) attached"
            currentText.isNotBlank() -> currentText
            imageUris.isNotEmpty() -> "📎 ${imageUris.size} file(s) attached"
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
                if (isTranslateMode && currentText.isNotBlank()) {
                    currentText = geminiRepository.translate(currentText, "English")
                }

                if (conversationId == null) {
                    val newConv = chatRepository.createNewConversation(displayText)
                    conversationId = newConv.id
                    _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
                } else {
                    chatRepository.addMessageToConversation(conversationId, displayText, true)
                }

                val history = currentMessages.dropLast(1)
                val modelName = getModelApiName(_uiState.value.selectedModel)

                var responseText = if (imageUris.isNotEmpty()) {
                    geminiRepository.generateResponseWithImage(
                        currentText, imageUris.first(), modelName, history, _uiState.value.customStyle
                    )
                } else {
                    geminiRepository.generateResponse(
                        currentText, modelName, history, _uiState.value.customStyle
                    )
                }

                if (isTranslateMode) {
                    responseText = geminiRepository.translate(responseText, _uiState.value.translateLanguage)
                }

                UsageTracker.recordRequest(getApplication(), _uiState.value.selectedModel)
                chatRepository.addMessageToConversation(conversationId, responseText, false)

                viewModelScope.launch {
                    geminiRepository.storeMessageEmbedding(responseText)
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

    // Voice
    fun onVoiceResult(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, isListening = false)
        if (text.isNotBlank()) onSend()
    }

    fun onCancelVoice() {
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    fun onStartVoiceInput(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            _uiState.value = _uiState.value.copy(isListening = true, inputText = "")
            launcher.launch(intent)
        } catch (e: Exception) {
            onCancelVoice()
        }
    }

    // Android TTS speak
    fun onSpeakMessage(messageId: String, text: String) {
        if (_uiState.value.isSpeaking && _uiState.value.speakingMessageId == messageId) {
            onStopSpeaking()
            return
        }
        _uiState.value = _uiState.value.copy(isSpeaking = true, speakingMessageId = messageId)
        androidTTS.speak(text) {
            _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null)
        }
    }

    fun onStopSpeaking() {
        androidTTS.stop()
        _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null)
    }

    // Like / Dislike
    fun onLikeMessage(messageId: String) {
        val liked = _uiState.value.likedMessages.toMutableSet()
        val disliked = _uiState.value.dislikedMessages.toMutableSet()
        if (messageId in liked) {
            liked.remove(messageId)
        } else {
            liked.add(messageId)
            disliked.remove(messageId)
        }
        _uiState.value = _uiState.value.copy(likedMessages = liked, dislikedMessages = disliked)
    }

    fun onDislikeMessage(messageId: String) {
        val liked = _uiState.value.likedMessages.toMutableSet()
        val disliked = _uiState.value.dislikedMessages.toMutableSet()
        if (messageId in disliked) {
            disliked.remove(messageId)
        } else {
            disliked.add(messageId)
            liked.remove(messageId)
        }
        _uiState.value = _uiState.value.copy(likedMessages = liked, dislikedMessages = disliked)
    }

    // Translate
    fun onTranslateMessage(messageId: String, text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTranslating = true)
            val translated = geminiRepository.translate(text, _uiState.value.translateLanguage)
            _uiState.value = _uiState.value.copy(
                translateResult = translated,
                showTranslateDialog = true,
                isTranslating = false
            )
        }
    }

    fun onDismissTranslateDialog() {
        _uiState.value = _uiState.value.copy(showTranslateDialog = false, translateResult = null)
    }

    fun onUpdateTranslateLanguage(lang: String) {
        _uiState.value = _uiState.value.copy(translateLanguage = lang)
    }

    // Chat history
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
            try {
                val msgs = chatRepository.getMessagesForConversationOnce(id)
                Log.d("ChatVM", "📂 Loaded ${msgs.size} messages for $id")
                _uiState.value = _uiState.value.copy(messages = msgs)
            } catch (e: Exception) {
                Log.e("ChatVM", "Failed to load conversation $id", e)
            }
        }
    }

    fun onDeleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) onNewChat()
        }
    }

    // Attachments
    fun onAttachmentTap() { _uiState.value = _uiState.value.copy(showAttachmentSheet = true) }
    fun dismissAttachmentSheet() { _uiState.value = _uiState.value.copy(showAttachmentSheet = false) }

    fun onImageSelected(uri: Uri) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        currentUris.add(uri)
        currentNames.add(uri.lastPathSegment ?: "File ${currentUris.size}")
        _uiState.value = _uiState.value.copy(selectedImageUris = currentUris, selectedFileNames = currentNames)
    }

    fun onImagesSelected(uris: List<Uri>) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        uris.forEach { uri ->
            currentUris.add(uri)
            currentNames.add(uri.lastPathSegment ?: "File ${currentUris.size}")
        }
        _uiState.value = _uiState.value.copy(selectedImageUris = currentUris, selectedFileNames = currentNames)
    }

    fun onFileSelected(uri: Uri, name: String) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        currentUris.add(uri)
        currentNames.add(name)
        _uiState.value = _uiState.value.copy(selectedImageUris = currentUris, selectedFileNames = currentNames)
    }

    fun onFilesSelected(uris: List<Uri>) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        uris.forEach { uri ->
            currentUris.add(uri)
            currentNames.add(uri.lastPathSegment ?: "File ${currentUris.size}")
        }
        _uiState.value = _uiState.value.copy(selectedImageUris = currentUris, selectedFileNames = currentNames)
    }

    fun removeSingleAttachment(index: Int) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        if (index in currentUris.indices) { currentUris.removeAt(index); currentNames.removeAt(index) }
        _uiState.value = _uiState.value.copy(selectedImageUris = currentUris, selectedFileNames = currentNames)
    }

    fun clearImageSelection() {
        _uiState.value = _uiState.value.copy(selectedImageUris = emptyList(), selectedFileNames = emptyList())
    }

    fun onMicTap() {
        _uiState.value = _uiState.value.copy(isListening = true, inputText = "")
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(isListening = false, inputText = "What's the weather today?")
        }
    }

    fun onToggleDrawer(isOpen: Boolean) { _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen) }
    fun onModelSelected(model: String) { _uiState.value = _uiState.value.copy(selectedModel = model) }
    fun onCustomStyleChanged(style: String) { _uiState.value = _uiState.value.copy(customStyle = style) }
    fun onShowStyleDialog() { _uiState.value = _uiState.value.copy(showStyleDialog = true) }
    fun onDismissStyleDialog() { _uiState.value = _uiState.value.copy(showStyleDialog = false) }
    fun onToggleTheme() { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme) }
}
