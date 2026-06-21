package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AndroidTTSManager
import com.example.data.ChatRepository
import com.example.data.GeminiRepository
import com.example.data.UsageTracker
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
        val selectedFileTypes: List<String> = emptyList(),
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

    private val chatRepository = ChatRepository(application)
    private val geminiRepository = GeminiRepository(application)
    private val androidTTS = AndroidTTSManager(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentGenerationJob: Job? = null
    private var lastUserPrompt: String = ""
    private var lastImageUris: List<Uri> = emptyList()

    init {
        viewModelScope.launch {
            chatRepository.getAllConversations().collect { conversations ->
                _uiState.value = _uiState.value.copy(conversations = conversations)
            }
        }
    }

    // ── File type detection ──
    private fun getFileType(uri: Uri): String {
        val mime = getApplication<Application>().contentResolver.getType(uri) ?: return "📎"
        return when {
            mime.startsWith("image/") -> "🖼️"
            mime.startsWith("video/") -> "🎬"
            mime.startsWith("audio/") -> "🎵"
            mime.contains("pdf") -> "📕"
            mime.contains("zip") || mime.contains("rar") || mime.contains("tar") || mime.contains("gzip") -> "📦"
            mime.contains("text/") || mime.contains("kotlin") || mime.contains("java") || mime.contains("python") -> "💻"
            mime.contains("word") || mime.contains("document") -> "📝"
            mime.contains("excel") || mime.contains("sheet") -> "📊"
            mime.contains("powerpoint") || mime.contains("presentation") -> "📽️"
            else -> "📎"
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onToggleTranslateMode() {
        _uiState.value = _uiState.value.copy(isTranslateMode = !_uiState.value.isTranslateMode)
    }

    fun onSend() {
        var currentText = _uiState.value.inputText.ifBlank { lastUserPrompt }
        val imageUris = _uiState.value.selectedImageUris.ifEmpty { lastImageUris }
        var conversationId = _uiState.value.currentConversationId
        val isTranslateMode = _uiState.value.isTranslateMode
        val isRegenerate = _uiState.value.inputText.isBlank() && lastUserPrompt.isNotBlank()

        if (currentText.isBlank() && imageUris.isEmpty()) return

        // Store for regenerate
        if (!isRegenerate) {
            lastUserPrompt = currentText
            lastImageUris = imageUris
        }

        val fileDescriptions = _uiState.value.selectedFileNames.zip(_uiState.value.selectedFileTypes).map { (name, type) ->
            "$type $name"
        }

        val displayText = when {
            currentText.isNotBlank() && imageUris.isNotEmpty() -> "$currentText\n📎 Attached: ${fileDescriptions.joinToString(", ")}"
            currentText.isNotBlank() -> currentText
            imageUris.isNotEmpty() -> "📎 Attached: ${fileDescriptions.joinToString(", ")}"
            else -> return
        }

        // Only add user message if not regenerating
        val currentMessages = if (!isRegenerate) {
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                text = displayText,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            val updated = _uiState.value.messages + userMessage
            _uiState.value = _uiState.value.copy(messages = updated)
            updated
        } else {
            // Remove the last AI message before regenerating
            val withoutLastAi = _uiState.value.messages.dropLastWhile { !it.isUser }
            _uiState.value = _uiState.value.copy(messages = withoutLastAi)
            withoutLastAi
        }

        _uiState.value = _uiState.value.copy(
            inputText = "",
            isAiThinking = true,
            isPaused = false,
            selectedImageUris = emptyList(),
            selectedFileNames = emptyList(),
            selectedFileTypes = emptyList()
        )

        currentGenerationJob = viewModelScope.launch {
            try {
                if (isTranslateMode && currentText.isNotBlank()) {
                    currentText = geminiRepository.translate(currentText, "English")
                }

                if (conversationId == null && !isRegenerate) {
                    val newConv = chatRepository.createNewConversation(displayText)
                    conversationId = newConv.id
                    _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
                } else if (!isRegenerate) {
                    chatRepository.addMessageToConversation(conversationId, displayText, true)
                }

                val history = currentMessages.dropLast(1)
                val modelName = getModelApiName(_uiState.value.selectedModel)

                val firstUri = imageUris.firstOrNull()
                val isImage = firstUri?.let { uri ->
                    getApplication<Application>().contentResolver.getType(uri)?.startsWith("image/") == true
                } ?: false

                var responseText = if (isImage && firstUri != null) {
                    geminiRepository.generateResponseWithImage(
                        currentText, firstUri, modelName, history, _uiState.value.customStyle
                    )
                } else {
                    val fileContent = if (firstUri != null && !isImage) {
                        readFileContent(firstUri)
                    } else ""
                    val fullPrompt = if (fileContent.isNotBlank()) {
                        "$currentText\n\n[File content]:\n$fileContent"
                    } else currentText
                    geminiRepository.generateResponse(fullPrompt, modelName, history, _uiState.value.customStyle)
                }

                if (isTranslateMode) {
                    responseText = geminiRepository.translate(responseText, _uiState.value.translateLanguage)
                }

                UsageTracker.recordRequest(getApplication(), _uiState.value.selectedModel)
                val convId = conversationId ?: ""
                   chatRepository.addMessageToConversation(convId, responseText, false)

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

    // ── Regenerate ──
    fun onRegenerate() {
        if (lastUserPrompt.isBlank() && _uiState.value.selectedImageUris.isEmpty()) {
            // Find the last user message
            val lastUserMsg = _uiState.value.messages.findLast { it.isUser }
            if (lastUserMsg != null) {
                lastUserPrompt = lastUserMsg.text
                _uiState.value = _uiState.value.copy(inputText = "")
                onSend()
            }
        } else {
            onSend()
        }
    }

    private fun readFileContent(uri: Uri): String {
        return try {
            getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText()?.take(4000) ?: ""
        } catch (e: Exception) { "" }
    }

    fun onPauseGeneration() { currentGenerationJob?.cancel(); currentGenerationJob = null; _uiState.value = _uiState.value.copy(isPaused = true, isAiThinking = false) }
    fun onResumeGeneration() { if (_uiState.value.isPaused) { _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = true); onSend() } }
    fun onStopGeneration() { currentGenerationJob?.cancel(); currentGenerationJob = null; _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = false); lastImageUris = emptyList() }

    fun onVoiceResult(text: String) { _uiState.value = _uiState.value.copy(inputText = text, isListening = false); if (text.isNotBlank()) onSend() }
    fun onCancelVoice() { _uiState.value = _uiState.value.copy(isListening = false) }

    fun onStartVoiceInput(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { _uiState.value = _uiState.value.copy(isListening = true, inputText = ""); launcher.launch(intent) }
        catch (e: Exception) { onCancelVoice() }
    }

    fun onSpeakMessage(messageId: String, text: String) {
        if (_uiState.value.isSpeaking && _uiState.value.speakingMessageId == messageId) { onStopSpeaking(); return }
        _uiState.value = _uiState.value.copy(isSpeaking = true, speakingMessageId = messageId)
        androidTTS.speak(text) { _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null) }
    }

    fun onStopSpeaking() { androidTTS.stop(); _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null) }

    fun onLikeMessage(messageId: String) {
        val liked = _uiState.value.likedMessages.toMutableSet()
        val disliked = _uiState.value.dislikedMessages.toMutableSet()
        if (messageId in liked) liked.remove(messageId) else { liked.add(messageId); disliked.remove(messageId) }
        _uiState.value = _uiState.value.copy(likedMessages = liked, dislikedMessages = disliked)
    }

    fun onDislikeMessage(messageId: String) {
        val liked = _uiState.value.likedMessages.toMutableSet()
        val disliked = _uiState.value.dislikedMessages.toMutableSet()
        if (messageId in disliked) disliked.remove(messageId) else { disliked.add(messageId); liked.remove(messageId) }
        _uiState.value = _uiState.value.copy(likedMessages = liked, dislikedMessages = disliked)
    }

    fun onTranslateMessage(messageId: String, text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTranslating = true)
            val translated = geminiRepository.translate(text, _uiState.value.translateLanguage)
            _uiState.value = _uiState.value.copy(translateResult = translated, showTranslateDialog = true, isTranslating = false)
        }
    }

    fun onDismissTranslateDialog() { _uiState.value = _uiState.value.copy(showTranslateDialog = false, translateResult = null) }
    fun onUpdateTranslateLanguage(lang: String) { _uiState.value = _uiState.value.copy(translateLanguage = lang) }

    fun onNewChat() {
        currentGenerationJob?.cancel(); currentGenerationJob = null
        lastUserPrompt = ""; lastImageUris = emptyList()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(), currentConversationId = null, isDrawerOpen = false,
            inputText = "", selectedImageUris = emptyList(), selectedFileNames = emptyList(),
            selectedFileTypes = emptyList(), isAiThinking = false, isPaused = false
        )
    }

    fun onSelectConversation(id: String) {
        currentGenerationJob?.cancel(); currentGenerationJob = null
        lastUserPrompt = ""; lastImageUris = emptyList()
        _uiState.value = _uiState.value.copy(currentConversationId = id, isDrawerOpen = false, isAiThinking = false, isPaused = false, messages = emptyList())
        viewModelScope.launch {
            try {
                val msgs = chatRepository.getMessagesForConversationOnce(id)
                Log.d("ChatVM", "📂 Loaded ${msgs.size} messages for $id")
                _uiState.value = _uiState.value.copy(messages = msgs)
                // Update last prompt for regenerate
                msgs.findLast { it.isUser }?.let { lastUserPrompt = it.text }
            } catch (e: Exception) { Log.e("ChatVM", "Failed to load conversation $id", e) }
        }
    }

    fun onDeleteConversation(id: String) {
        viewModelScope.launch { chatRepository.deleteConversation(id); if (_uiState.value.currentConversationId == id) onNewChat() }
    }

    fun onAttachmentTap() { _uiState.value = _uiState.value.copy(showAttachmentSheet = true) }
    fun dismissAttachmentSheet() { _uiState.value = _uiState.value.copy(showAttachmentSheet = false) }

    fun onImageSelected(uri: Uri) { addAttachment(uri, uri.lastPathSegment ?: "File") }
    fun onImagesSelected(uris: List<Uri>) { uris.forEach { addAttachment(it, it.lastPathSegment ?: "File") } }
    fun onFileSelected(uri: Uri, name: String) { addAttachment(uri, name) }
    fun onFilesSelected(uris: List<Uri>) { uris.forEach { addAttachment(it, it.lastPathSegment ?: "File") } }

    private fun addAttachment(uri: Uri, name: String) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        val currentTypes = _uiState.value.selectedFileTypes.toMutableList()
        currentUris.add(uri); currentNames.add(name); currentTypes.add(getFileType(uri))
        _uiState.value = _uiState.value.copy(selectedImageUris = currentUris, selectedFileNames = currentNames, selectedFileTypes = currentTypes)
    }

    fun removeSingleAttachment(index: Int) {
        val currentUris = _uiState.value.selectedImageUris.toMutableList()
        val currentNames = _uiState.value.selectedFileNames.toMutableList()
        val currentTypes = _uiState.value.selectedFileTypes.toMutableList()
        if (index in currentUris.indices) { currentUris.removeAt(index); currentNames.removeAt(index); currentTypes.removeAt(index) }
        _uiState.value = _uiState.value.copy(selectedImageUris = currentUris, selectedFileNames = currentNames, selectedFileTypes = currentTypes)
    }

    fun clearImageSelection() { _uiState.value = _uiState.value.copy(selectedImageUris = emptyList(), selectedFileNames = emptyList(), selectedFileTypes = emptyList()) }

    fun onMicTap() {
        _uiState.value = _uiState.value.copy(isListening = true, inputText = "")
        viewModelScope.launch { delay(2000); _uiState.value = _uiState.value.copy(isListening = false, inputText = "What's the weather today?") }
    }

    fun onToggleDrawer(isOpen: Boolean) { _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen) }
    fun onModelSelected(model: String) { _uiState.value = _uiState.value.copy(selectedModel = model) }
    fun onCustomStyleChanged(style: String) { _uiState.value = _uiState.value.copy(customStyle = style) }
    fun onShowStyleDialog() { _uiState.value = _uiState.value.copy(showStyleDialog = true) }
    fun onDismissStyleDialog() { _uiState.value = _uiState.value.copy(showStyleDialog = false) }
    fun onToggleTheme() { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme) }
}
