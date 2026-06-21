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
        val isSearchMode: Boolean = false,
        val translateLanguage: String = "English",
        val isTranslating: Boolean = false,
        val translateResult: String? = null,
        val showTranslateDialog: Boolean = false,
        val likedMessages: Set<String> = emptySet(),
        val dislikedMessages: Set<String> = emptySet()
    )

    companion object {
        val availableModels = listOf(
            "Gemini 3.5 Flash", "Gemini 3 Flash Preview", "Gemini 3.1 Flash-Lite",
            "Gemini 2.5 Pro", "Gemini 2.5 Flash", "Gemini 2.5 Flash-Lite",
            "Gemini 2.5 Flash-Lite Preview", "Gemma 4 26B", "Gemma 4 31B"
        )

        fun getModelApiName(displayName: String): String = when (displayName) {
            "Gemini 3.5 Flash" -> "models/gemini-3.5-flash"
            "Gemini 3 Flash Preview" -> "models/gemini-3-flash-preview"
            "Gemini 3.1 Flash-Lite" -> "models/gemini-3.1-flash-lite"
            "Gemini 2.5 Pro" -> "models/gemini-2.5-pro"
            "Gemini 2.5 Flash" -> "models/gemini-2.5-flash"
            "Gemini 2.5 Flash-Lite" -> "models/gemini-2.5-flash-lite"
            "Gemini 2.5 Flash-Lite Preview" -> "models/gemini-2.5-flash-lite-preview-09-2025"
            "Gemma 4 26B" -> "models/gemma-4-26b-a4b-it"
            "Gemma 4 31B" -> "models/gemma-4-31b-it"
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
            chatRepository.getAllConversations().collect { _uiState.value = _uiState.value.copy(conversations = it) }
        }
    }

    private fun getFileType(uri: Uri): String {
        val mime = getApplication<Application>().contentResolver.getType(uri) ?: return "📎"
        return when {
            mime.startsWith("image/") -> "🖼️"
            mime.startsWith("video/") -> "🎬"
            mime.startsWith("audio/") -> "🎵"
            mime.contains("pdf") -> "📕"
            mime.contains("zip") || mime.contains("rar") -> "📦"
            mime.contains("text/") || mime.contains("kotlin") || mime.contains("java") -> "💻"
            else -> "📎"
        }
    }

    fun onInputChanged(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }
    fun onToggleTranslateMode() { _uiState.value = _uiState.value.copy(isTranslateMode = !_uiState.value.isTranslateMode) }
    fun onToggleSearchMode() { _uiState.value = _uiState.value.copy(isSearchMode = !_uiState.value.isSearchMode) }

    fun onSend() {
        var currentText = _uiState.value.inputText.ifBlank { lastUserPrompt }
        val imageUris = _uiState.value.selectedImageUris.ifEmpty { lastImageUris }
        var conversationId: String? = _uiState.value.currentConversationId
        val isRegenerate = _uiState.value.inputText.isBlank() && lastUserPrompt.isNotBlank()

        if (currentText.isBlank() && imageUris.isEmpty()) return
        if (!isRegenerate) { lastUserPrompt = currentText; lastImageUris = imageUris }

        val fileDescriptions = _uiState.value.selectedFileNames.zip(_uiState.value.selectedFileTypes).map { (n, t) -> "$t $n" }
        val displayText = when {
            currentText.isNotBlank() && imageUris.isNotEmpty() -> "$currentText\n📎 ${fileDescriptions.joinToString(", ")}"
            currentText.isNotBlank() -> currentText
            imageUris.isNotEmpty() -> "📎 ${fileDescriptions.joinToString(", ")}"
            else -> return
        }

        val currentMessages = if (!isRegenerate) {
            val um = Message(UUID.randomUUID().toString(), displayText, true, System.currentTimeMillis())
            _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + um)
            _uiState.value.messages
        } else {
            _uiState.value = _uiState.value.copy(messages = _uiState.value.messages.dropLastWhile { !it.isUser })
            _uiState.value.messages
        }

        _uiState.value = _uiState.value.copy(inputText = "", isAiThinking = true, isPaused = false,
            selectedImageUris = emptyList(), selectedFileNames = emptyList(), selectedFileTypes = emptyList())

        currentGenerationJob = viewModelScope.launch {
            try {
                if (_uiState.value.isTranslateMode && currentText.isNotBlank())
                    currentText = geminiRepository.translate(currentText, "English")

                val safeConvId = if (conversationId == null && !isRegenerate) {
                    val nc = chatRepository.createNewConversation(displayText)
                    conversationId = nc.id; _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
                    conversationId
                } else {
                    if (!isRegenerate && conversationId != null) chatRepository.addMessageToConversation(conversationId, displayText, true)
                    conversationId ?: ""
                }

                val history = currentMessages.dropLast(1)
                val modelName = getModelApiName(_uiState.value.selectedModel)
                val firstUri = imageUris.firstOrNull()
                val isImage = firstUri?.let { getApplication<Application>().contentResolver.getType(it)?.startsWith("image/") == true } ?: false

                var responseText = if (isImage && firstUri != null) {
                    geminiRepository.generateResponseWithImage(currentText, firstUri, modelName, history, _uiState.value.customStyle)
                } else {
                    val fc = if (firstUri != null && !isImage) readFileContent(firstUri) else ""
                    val fp = if (fc.isNotBlank()) "$currentText\n\n[File]:\n$fc" else currentText
                    geminiRepository.generateResponse(fp, modelName, history, _uiState.value.customStyle, _uiState.value.isSearchMode)
                }

                if (_uiState.value.isTranslateMode) responseText = geminiRepository.translate(responseText, _uiState.value.translateLanguage)
                UsageTracker.recordRequest(getApplication(), _uiState.value.selectedModel)
                chatRepository.addMessageToConversation(safeConvId, responseText, false)
                viewModelScope.launch { geminiRepository.storeMessageEmbedding(responseText) }

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), responseText, false, System.currentTimeMillis()),
                    isAiThinking = false
                )
                lastUserPrompt = ""; lastImageUris = emptyList()
            } catch (e: CancellationException) {
                if (!_uiState.value.isPaused) _uiState.value = _uiState.value.copy(isAiThinking = false, messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), "⏹️ Stopped.", false, System.currentTimeMillis()))
                throw e
            } catch (e: Exception) { Log.e("ChatVM", "Send failed", e); _uiState.value = _uiState.value.copy(isAiThinking = false) }
        }
    }

    fun onRegenerate() {
        if (lastUserPrompt.isBlank()) _uiState.value.messages.findLast { it.isUser }?.let { lastUserPrompt = it.text }
        if (lastUserPrompt.isNotBlank()) { _uiState.value = _uiState.value.copy(inputText = ""); onSend() }
    }

    private fun readFileContent(uri: Uri): String = try { getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.readText()?.take(4000) ?: "" } catch (e: Exception) { "" }

    fun onPauseGeneration() { currentGenerationJob?.cancel(); currentGenerationJob = null; _uiState.value = _uiState.value.copy(isPaused = true, isAiThinking = false) }
    fun onResumeGeneration() { if (_uiState.value.isPaused) { _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = true); onSend() } }
    fun onStopGeneration() { currentGenerationJob?.cancel(); currentGenerationJob = null; _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = false) }

    fun onVoiceResult(text: String) { _uiState.value = _uiState.value.copy(inputText = text, isListening = false); if (text.isNotBlank()) onSend() }
    fun onCancelVoice() { _uiState.value = _uiState.value.copy(isListening = false) }

    fun onStartVoiceInput(launcher: ActivityResultLauncher<Intent>) {
        try { _uiState.value = _uiState.value.copy(isListening = true, inputText = ""); launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak..."); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1) }) }
        catch (e: Exception) { onCancelVoice() }
    }

    fun onSpeakMessage(messageId: String, text: String) {
        if (_uiState.value.isSpeaking && _uiState.value.speakingMessageId == messageId) { onStopSpeaking(); return }
        _uiState.value = _uiState.value.copy(isSpeaking = true, speakingMessageId = messageId)
        androidTTS.speak(text) { _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null) }
    }
    fun onStopSpeaking() { androidTTS.stop(); _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null) }

    fun onLikeMessage(id: String) { val l = _uiState.value.likedMessages.toMutableSet(); val d = _uiState.value.dislikedMessages.toMutableSet(); if (id in l) l.remove(id) else { l.add(id); d.remove(id) }; _uiState.value = _uiState.value.copy(likedMessages = l, dislikedMessages = d) }
    fun onDislikeMessage(id: String) { val l = _uiState.value.likedMessages.toMutableSet(); val d = _uiState.value.dislikedMessages.toMutableSet(); if (id in d) d.remove(id) else { d.add(id); l.remove(id) }; _uiState.value = _uiState.value.copy(likedMessages = l, dislikedMessages = d) }

    fun onTranslateMessage(messageId: String, text: String) {
        viewModelScope.launch { _uiState.value = _uiState.value.copy(isTranslating = true); val t = geminiRepository.translate(text, _uiState.value.translateLanguage); _uiState.value = _uiState.value.copy(translateResult = t, showTranslateDialog = true, isTranslating = false) }
    }
    fun onDismissTranslateDialog() { _uiState.value = _uiState.value.copy(showTranslateDialog = false, translateResult = null) }
    fun onUpdateTranslateLanguage(lang: String) { _uiState.value = _uiState.value.copy(translateLanguage = lang) }

    fun onNewChat() { currentGenerationJob?.cancel(); currentGenerationJob = null; lastUserPrompt = ""; lastImageUris = emptyList(); _uiState.value = _uiState.value.copy(messages = emptyList(), currentConversationId = null, isDrawerOpen = false, inputText = "", selectedImageUris = emptyList(), selectedFileNames = emptyList(), selectedFileTypes = emptyList(), isAiThinking = false, isPaused = false) }
    fun onSelectConversation(id: String) { currentGenerationJob?.cancel(); currentGenerationJob = null; _uiState.value = _uiState.value.copy(currentConversationId = id, isDrawerOpen = false, isAiThinking = false, isPaused = false, messages = emptyList()); viewModelScope.launch { try { val msgs = chatRepository.getMessagesForConversationOnce(id); _uiState.value = _uiState.value.copy(messages = msgs); msgs.findLast { it.isUser }?.let { lastUserPrompt = it.text } } catch (e: Exception) {} } }
    fun onDeleteConversation(id: String) { viewModelScope.launch { chatRepository.deleteConversation(id); if (_uiState.value.currentConversationId == id) onNewChat() } }

    fun onAttachmentTap() { _uiState.value = _uiState.value.copy(showAttachmentSheet = true) }
    fun dismissAttachmentSheet() { _uiState.value = _uiState.value.copy(showAttachmentSheet = false) }
    fun onImageSelected(uri: Uri) { addAttachment(uri, uri.lastPathSegment ?: "File") }
    fun onImagesSelected(uris: List<Uri>) { uris.forEach { addAttachment(it, it.lastPathSegment ?: "File") } }
    fun onFileSelected(uri: Uri, name: String) { addAttachment(uri, name) }
    fun onFilesSelected(uris: List<Uri>) { uris.forEach { addAttachment(it, it.lastPathSegment ?: "File") } }
    private fun addAttachment(uri: Uri, name: String) { val u = _uiState.value.selectedImageUris.toMutableList(); val n = _uiState.value.selectedFileNames.toMutableList(); val t = _uiState.value.selectedFileTypes.toMutableList(); u.add(uri); n.add(name); t.add(getFileType(uri)); _uiState.value = _uiState.value.copy(selectedImageUris = u, selectedFileNames = n, selectedFileTypes = t) }
    fun removeSingleAttachment(i: Int) { val u = _uiState.value.selectedImageUris.toMutableList(); val n = _uiState.value.selectedFileNames.toMutableList(); val t = _uiState.value.selectedFileTypes.toMutableList(); if (i in u.indices) { u.removeAt(i); n.removeAt(i); t.removeAt(i) }; _uiState.value = _uiState.value.copy(selectedImageUris = u, selectedFileNames = n, selectedFileTypes = t) }
    fun clearImageSelection() { _uiState.value = _uiState.value.copy(selectedImageUris = emptyList(), selectedFileNames = emptyList(), selectedFileTypes = emptyList()) }

    fun onMicTap() { _uiState.value = _uiState.value.copy(isListening = true, inputText = ""); viewModelScope.launch { delay(2000); _uiState.value = _uiState.value.copy(isListening = false, inputText = "What's the weather?") } }
    fun onToggleDrawer(isOpen: Boolean) { _uiState.value = _uiState.value.copy(isDrawerOpen = isOpen) }
    fun onModelSelected(model: String) { _uiState.value = _uiState.value.copy(selectedModel = model) }
    fun onCustomStyleChanged(style: String) { _uiState.value = _uiState.value.copy(customStyle = style) }
    fun onShowStyleDialog() { _uiState.value = _uiState.value.copy(showStyleDialog = true) }
    fun onDismissStyleDialog() { _uiState.value = _uiState.value.copy(showStyleDialog = false) }
    fun onToggleTheme() { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme) }
}
