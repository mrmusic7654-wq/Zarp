package com.example.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.AgentLoopManager
import com.example.data.AndroidTTSManager
import com.example.data.AutomationEngine
import com.example.data.BuildMonitor
import com.example.data.ChatRepository
import com.example.data.CodeExecutionManager
import com.example.data.DeviceController
import com.example.data.GeminiRepository
import com.example.data.GitHubAgent
import com.example.data.KeyManager
import com.example.data.StreamingVoiceManager
import com.example.data.SystemNotificationManager
import com.example.data.UsageTracker
import com.example.data.VoiceUIManager
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
        val tasks: List<Conversation> = emptyList(),
        val projects: List<ChatRepository.ProjectInfo> = emptyList(),
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
        val isAgentMode: Boolean = false,
        val isAutomationMode: Boolean = false,
        val isVoiceMode: Boolean = false,
        val translateLanguage: String = "English",
        val isTranslating: Boolean = false,
        val translateResult: String? = null,
        val showTranslateDialog: Boolean = false,
        val likedMessages: Set<String> = emptySet(),
        val dislikedMessages: Set<String> = emptySet(),
        val agentProgress: AgentLoopManager.AgentProgress? = null,
        val agentTaskResult: AgentLoopManager.AgentResult? = null,
        val buildStatus: BuildMonitor.BuildStatus? = null,
        val buildLog: BuildMonitor.BuildLog? = null,
        val isFixingBuild: Boolean = false,
        val showBuildNotification: Boolean = false,
        val currentTask: AgentLoopManager.AgentTask? = null,
        val voiceState: StreamingVoiceManager.VoiceState = StreamingVoiceManager.VoiceState(),
        val automationProgress: AutomationEngine.AutomationProgress? = null,
        val automationResult: AutomationEngine.AutomationResult? = null,
        val errorMessage: String? = null,
        val retryCount: Int = 0,
        val snackbarMessage: String? = null,
        val dailyUsage: DailyUsage = DailyUsage()
    )

    data class DailyUsage(val requestsToday: Int = 0, val tokensUsed: Long = 0, val limitReached: Boolean = false)

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
    private val codeExecutionManager by lazy { CodeExecutionManager(KeyManager.getGeminiKey(application) ?: "") }
    private val gitHubAgent by lazy { GitHubAgent(KeyManager.getGithubKey(application) ?: "") }
    private val agentLoopManager by lazy { AgentLoopManager(geminiRepository, codeExecutionManager, gitHubAgent, application) }

    val streamingVoiceManager = StreamingVoiceManager(application)
    val deviceController = DeviceController(application)
    val automationEngine by lazy { AutomationEngine(deviceController, geminiRepository, application) }
    val notificationManager = SystemNotificationManager(application)
    val voiceUIManager = VoiceUIManager(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentGenerationJob: Job? = null
    private var lastUserPrompt: String = ""
    private var lastImageUris: List<Uri> = emptyList()
    private var totalRequestsToday = 0

    init {
        viewModelScope.launch { chatRepository.getAllConversations().collect { _uiState.value = _uiState.value.copy(conversations = it) } }
        viewModelScope.launch { chatRepository.getAllTasks().collect { _uiState.value = _uiState.value.copy(tasks = it) } }
        viewModelScope.launch { chatRepository.getAllProjects().collect { _uiState.value = _uiState.value.copy(projects = it) } }
        viewModelScope.launch {
            streamingVoiceManager.voiceState.collect { vs ->
                _uiState.value = _uiState.value.copy(voiceState = vs)
                if (vs.finalText.isNotBlank() && _uiState.value.isVoiceMode) { _uiState.value = _uiState.value.copy(inputText = vs.finalText); onSend() }
            }
        }
    }

    private fun getFileType(uri: Uri): String {
        val mime = getApplication<Application>().contentResolver.getType(uri) ?: return "📎"
        return when { mime.startsWith("image/") -> "🖼️"; mime.startsWith("video/") -> "🎬"; mime.startsWith("audio/") -> "🎵"; mime.contains("pdf") -> "📕"; mime.contains("zip") || mime.contains("rar") -> "📦"; mime.contains("text/") || mime.contains("kotlin") || mime.contains("java") -> "💻"; else -> "📎" }
    }

    fun onInputChanged(text: String) { _uiState.value = _uiState.value.copy(inputText = text, errorMessage = null, snackbarMessage = null) }
    fun onToggleTranslateMode() { _uiState.value = _uiState.value.copy(isTranslateMode = !_uiState.value.isTranslateMode) }
    fun onToggleSearchMode() { _uiState.value = _uiState.value.copy(isSearchMode = !_uiState.value.isSearchMode) }
    fun onToggleAgentMode() { _uiState.value = _uiState.value.copy(isAgentMode = !_uiState.value.isAgentMode, isAutomationMode = false) }
    fun onToggleAutomationMode() { _uiState.value = _uiState.value.copy(isAutomationMode = !_uiState.value.isAutomationMode, isAgentMode = false) }
    fun onToggleVoiceMode() { val newMode = !_uiState.value.isVoiceMode; _uiState.value = _uiState.value.copy(isVoiceMode = newMode); if (newMode) voiceUIManager.show() else { voiceUIManager.hide(); streamingVoiceManager.stopListening() } }
    fun onDismissSnackbar() { _uiState.value = _uiState.value.copy(snackbarMessage = null) }

    fun onSend() { if (_uiState.value.inputText.isBlank() && _uiState.value.selectedImageUris.isEmpty()) return; when { _uiState.value.isAutomationMode -> onSendAutomation(); _uiState.value.isAgentMode -> onSendAgent(); else -> onSendChat() } }
    fun onRetry() { _uiState.value = _uiState.value.copy(errorMessage = null, retryCount = _uiState.value.retryCount + 1); onSend() }

    private fun onSendChat() {
        var currentText = _uiState.value.inputText.ifBlank { lastUserPrompt }
        val imageUris = _uiState.value.selectedImageUris.ifEmpty { lastImageUris }
        var conversationId: String? = _uiState.value.currentConversationId
        val isRegenerate = _uiState.value.inputText.isBlank() && lastUserPrompt.isNotBlank()
        if (currentText.isBlank() && imageUris.isEmpty()) return
        if (!isRegenerate) { lastUserPrompt = currentText; lastImageUris = imageUris }

        val fileDescriptions = _uiState.value.selectedFileNames.zip(_uiState.value.selectedFileTypes).map { (n, t) -> "$t $n" }
        val displayText = when { currentText.isNotBlank() && imageUris.isNotEmpty() -> "$currentText\n📎 ${fileDescriptions.joinToString()}"; currentText.isNotBlank() -> currentText; imageUris.isNotEmpty() -> "📎 ${fileDescriptions.joinToString()}"; else -> return }
        val currentMessages = if (!isRegenerate) { val um = Message(UUID.randomUUID().toString(), displayText, true, System.currentTimeMillis()); _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + um); _uiState.value.messages } else { _uiState.value = _uiState.value.copy(messages = _uiState.value.messages.dropLastWhile { !it.isUser }); _uiState.value.messages }
        _uiState.value = _uiState.value.copy(inputText = "", isAiThinking = true, isPaused = false, selectedImageUris = emptyList(), selectedFileNames = emptyList(), selectedFileTypes = emptyList(), errorMessage = null, snackbarMessage = null)

        currentGenerationJob = viewModelScope.launch {
            try {
                if (_uiState.value.isTranslateMode && currentText.isNotBlank()) currentText = geminiRepository.translate(currentText, "English")
                val safeConvId: String = if (conversationId == null && !isRegenerate) {
                    val nc = chatRepository.createNewConversation(displayText); conversationId = nc.id; _uiState.value = _uiState.value.copy(currentConversationId = conversationId); conversationId
                } else {
                    if (!isRegenerate && conversationId != null) chatRepository.addMessageToConversation(conversationId, displayText, true); conversationId ?: ""
                }
                val modelName = getModelApiName(_uiState.value.selectedModel)
                val firstUri = imageUris.firstOrNull()
                val isImage = firstUri?.let { getApplication<Application>().contentResolver.getType(it)?.startsWith("image/") == true } ?: false
                var responseText: String = if (isImage && firstUri != null) geminiRepository.generateResponseWithImage(currentText, firstUri, modelName, currentMessages.dropLast(1), _uiState.value.customStyle)
                else { val fc = if (firstUri != null && !isImage) readFileContent(firstUri) else ""; val fp = if (fc.isNotBlank()) "$currentText\n\n[File]:\n$fc" else currentText; geminiRepository.generateResponse(fp, modelName, currentMessages.dropLast(1), _uiState.value.customStyle, _uiState.value.isSearchMode) }
                if (_uiState.value.isTranslateMode) responseText = geminiRepository.translate(responseText, _uiState.value.translateLanguage)
                totalRequestsToday++; UsageTracker.recordRequest(getApplication(), _uiState.value.selectedModel)
                chatRepository.addMessageToConversation(safeConvId, responseText, false)
                viewModelScope.launch { geminiRepository.storeMessageEmbedding(responseText) }
                _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), responseText, false, System.currentTimeMillis()), isAiThinking = false, dailyUsage = DailyUsage(totalRequestsToday, 0, false))
                lastUserPrompt = ""; lastImageUris = emptyList()
                notifyNewMessage(responseText)
            } catch (e: CancellationException) { if (!_uiState.value.isPaused) _uiState.value = _uiState.value.copy(isAiThinking = false, messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), "⏹️ Stopped.", false, System.currentTimeMillis()), snackbarMessage = "Generation stopped"); throw e }
            catch (e: Exception) { Log.e("ChatVM", "Send failed", e); _uiState.value = _uiState.value.copy(isAiThinking = false, errorMessage = e.localizedMessage ?: "Failed to send", snackbarMessage = "Failed to send. Tap retry.") }
        }
    }

    private fun onSendAgent() {
        val currentText = _uiState.value.inputText.ifBlank { lastUserPrompt }
        if (currentText.isBlank()) return
        lastUserPrompt = currentText
        val conversationId = _uiState.value.currentConversationId
        val userMessage = Message(UUID.randomUUID().toString(), "🤖 Agent: $currentText", true, System.currentTimeMillis())
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + userMessage, inputText = "", isAiThinking = true, isPaused = false, agentProgress = null, agentTaskResult = null, buildStatus = null, buildLog = null, showBuildNotification = false, errorMessage = null, snackbarMessage = null)

        currentGenerationJob = viewModelScope.launch {
            try {
                conversationId?.let { chatRepository.addMessageToConversation(it, "🤖 Agent: $currentText", true) }
                val task = agentLoopManager.executeAgentTask(currentText, _uiState.value.messages.dropLast(1)) { progress ->
                    _uiState.value = _uiState.value.copy(agentProgress = progress)
                    conversationId?.let { chatRepository.updateTaskProgress(it, ChatRepository.TaskProgress(it, progress.status.name, progress.percentage, progress.message)) }
                }
                val responseText = task.result?.summary ?: "Agent task completed."
                _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), responseText, false, System.currentTimeMillis()), isAiThinking = false, agentTaskResult = task.result, currentTask = task, buildStatus = task.buildStatus, buildLog = task.buildLog, showBuildNotification = task.buildStatus?.isFailure == true, snackbarMessage = if (task.buildStatus?.isSuccess == true) "✅ Build passed!" else if (task.buildStatus?.isFailure == true) "❌ Build failed" else null)
                conversationId?.let { chatRepository.addMessageToConversation(it, responseText, false) }
                conversationId?.let { id -> val taskTitle = if (currentText.length > 40) currentText.take(40) + "..." else currentText; chatRepository.saveConversationAsTask(id, taskTitle); chatRepository.addTaskMessage(id, "🤖 Agent: $currentText", true); chatRepository.addTaskMessage(id, responseText, false) }
                task.result?.let { result -> conversationId?.let { id -> chatRepository.saveProject(ChatRepository.ProjectInfo(id, task.intent?.appName ?: "Project", result.repoUrl ?: "", task.repoOwner ?: "", task.repoName ?: "", result.totalFiles, result.buildStatus, task.createdAt, System.currentTimeMillis(), task.intent?.architecture, result.fixAttempts)) } }
                if (task.buildStatus?.isFailure == true) notifyBuildFailed(task) else if (task.result?.repoUrl != null) notifyAgentComplete(task)
                lastUserPrompt = ""
            } catch (e: CancellationException) { _uiState.value = _uiState.value.copy(isAiThinking = false, snackbarMessage = "Agent stopped"); throw e }
            catch (e: Exception) { Log.e("ChatVM", "Agent failed", e); _uiState.value = _uiState.value.copy(isAiThinking = false, errorMessage = e.localizedMessage ?: "Agent failed", snackbarMessage = "Agent task failed") }
        }
    }

    private fun onSendAutomation() {
        val currentText = _uiState.value.inputText.ifBlank { lastUserPrompt }
        if (currentText.isBlank()) return
        lastUserPrompt = currentText
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), "🎮 Automation: $currentText", true, System.currentTimeMillis()), inputText = "", isAiThinking = true, isPaused = false, automationProgress = null, automationResult = null, errorMessage = null, snackbarMessage = null)

        currentGenerationJob = viewModelScope.launch {
            try {
                val task = automationEngine.executeAutomation(userRequest = currentText, onProgress = { progress -> _uiState.value = _uiState.value.copy(automationProgress = progress) })
                val responseText = task.result?.summary ?: "Automation completed."
                _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), responseText, false, System.currentTimeMillis()), isAiThinking = false, automationResult = task.result, snackbarMessage = if (task.result?.success == true) "✅ Automation done" else "⚠️ Automation had issues")
                lastUserPrompt = ""
            } catch (e: CancellationException) { _uiState.value = _uiState.value.copy(isAiThinking = false, snackbarMessage = "Automation stopped"); throw e }
            catch (e: Exception) { Log.e("ChatVM", "Automation failed", e); _uiState.value = _uiState.value.copy(isAiThinking = false, errorMessage = e.localizedMessage ?: "Automation failed", snackbarMessage = "Automation failed") }
        }
    }

    fun onStartVoiceInput() { streamingVoiceManager.startListening(); voiceUIManager.setListeningState(true) }
    fun onStopVoiceInput() { streamingVoiceManager.stopListening(); voiceUIManager.setListeningState(false) }

    private fun notifyNewMessage(message: String) {
        try { val intent = Intent(getApplication(), MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }; notificationManager.notifyNewMessage(message, _uiState.value.conversations.find { it.id == _uiState.value.currentConversationId }?.title ?: "Zarp", null, PendingIntent.getActivity(getApplication(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) } catch (e: Exception) { Log.w("ChatVM", "Notification failed", e) }
    }

    private fun notifyBuildFailed(task: AgentLoopManager.AgentTask) {
        try { val fixIntent = PendingIntent.getActivity(getApplication(), 1, Intent(getApplication(), MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra("fix_build", task.id) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); notificationManager.notifyBuildFailed(task.repoName ?: "repo", task.result?.repoUrl ?: "", task.buildLog?.errors?.size ?: 0, task.buildLog?.errors?.firstOrNull() ?: "Unknown error", fixIntent, PendingIntent.getActivity(getApplication(), 2, Intent(Intent.ACTION_VIEW, Uri.parse(task.result?.repoUrl ?: "")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_IMMUTABLE)) } catch (e: Exception) { Log.w("ChatVM", "Build notification failed", e) }
    }

    private fun notifyAgentComplete(task: AgentLoopManager.AgentTask) {
        try { notificationManager.notifyAgentComplete(task.result?.summary ?: "", task.result?.repoUrl, task.result?.totalFiles ?: 0, PendingIntent.getActivity(getApplication(), 3, Intent(Intent.ACTION_VIEW, Uri.parse(task.result?.repoUrl ?: "")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_IMMUTABLE)) } catch (e: Exception) { Log.w("ChatVM", "Agent notification failed", e) }
    }

    fun onFixAndRebuild() {
        val task = _uiState.value.currentTask ?: return
        _uiState.value = _uiState.value.copy(isFixingBuild = true, isAiThinking = true, showBuildNotification = false, snackbarMessage = "🔧 Fixing build...")
        currentGenerationJob = viewModelScope.launch {
            try {
                val additionalContext = if (_uiState.value.buildLog != null) "Build errors:\n${_uiState.value.buildLog!!.errors.joinToString("\n")}" else ""
                val updatedTask = agentLoopManager.continueFailedTask(task, additionalContext) { _uiState.value = _uiState.value.copy(agentProgress = it) }
                val responseText = updatedTask.result?.summary ?: "Fix applied."
                _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + Message(UUID.randomUUID().toString(), responseText, false, System.currentTimeMillis()), isAiThinking = false, isFixingBuild = false, agentTaskResult = updatedTask.result, currentTask = updatedTask, buildStatus = updatedTask.buildStatus, buildLog = updatedTask.buildLog, snackbarMessage = if (updatedTask.buildStatus?.isSuccess == true) "✅ Fix successful!" else "⚠️ Fix applied, check build")
                _uiState.value.currentConversationId?.let { id -> chatRepository.addMessageToConversation(id, responseText, false); chatRepository.addTaskMessage(id, responseText, false) }
            } catch (e: CancellationException) { _uiState.value = _uiState.value.copy(isAiThinking = false, isFixingBuild = false); throw e }
            catch (e: Exception) { Log.e("ChatVM", "Fix failed", e); _uiState.value = _uiState.value.copy(isAiThinking = false, isFixingBuild = false, errorMessage = e.localizedMessage ?: "Fix failed", snackbarMessage = "Fix failed") }
        }
    }

    fun onDismissBuildNotification() { _uiState.value = _uiState.value.copy(showBuildNotification = false) }
    fun onDismissError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    fun onSelectTask(id: String) { currentGenerationJob?.cancel(); currentGenerationJob = null; lastUserPrompt = ""; lastImageUris = emptyList(); _uiState.value = _uiState.value.copy(currentConversationId = id, isDrawerOpen = false, isAiThinking = false, isPaused = false, messages = emptyList(), snackbarMessage = null); viewModelScope.launch { try { val msgs = chatRepository.getTaskMessagesOnce(id); _uiState.value = _uiState.value.copy(messages = if (msgs.isNotEmpty()) msgs else chatRepository.getMessagesForConversationOnce(id)); _uiState.value.messages.findLast { it.isUser }?.let { lastUserPrompt = it.text } } catch (e: Exception) { Log.e("ChatVM", "Load task failed", e) } } }
    fun onDeleteTask(id: String) { viewModelScope.launch { chatRepository.deleteTask(id); if (_uiState.value.currentConversationId == id) onNewChat() } }
    fun onSelectProject(project: ChatRepository.ProjectInfo) { onSelectTask(project.id); if (project.repoUrl.isNotBlank()) { try { getApplication<Application>().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(project.repoUrl)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) } catch (e: Exception) {} } }

    fun onRegenerate() { if (lastUserPrompt.isBlank()) _uiState.value.messages.findLast { it.isUser }?.let { lastUserPrompt = it.text }; if (lastUserPrompt.isNotBlank()) { _uiState.value = _uiState.value.copy(inputText = ""); onSend() } }
    private fun readFileContent(uri: Uri): String = try { getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.readText()?.take(4000) ?: "" } catch (e: Exception) { "" }

    fun onPauseGeneration() { currentGenerationJob?.cancel(); currentGenerationJob = null; _uiState.value = _uiState.value.copy(isPaused = true, isAiThinking = false) }
    fun onResumeGeneration() { if (_uiState.value.isPaused) { _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = true); onSend() } }
    fun onStopGeneration() { currentGenerationJob?.cancel(); currentGenerationJob = null; _uiState.value = _uiState.value.copy(isPaused = false, isAiThinking = false) }

    fun onVoiceResult(text: String) { _uiState.value = _uiState.value.copy(inputText = text, isListening = false); if (text.isNotBlank()) onSend() }
    fun onCancelVoice() { _uiState.value = _uiState.value.copy(isListening = false) }
    fun onStartVoiceInput(launcher: ActivityResultLauncher<Intent>) { try { _uiState.value = _uiState.value.copy(isListening = true, inputText = ""); launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak..."); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1) }) } catch (e: Exception) { onCancelVoice() } }

    fun onSpeakMessage(messageId: String, text: String) { if (_uiState.value.isSpeaking && _uiState.value.speakingMessageId == messageId) { onStopSpeaking(); return }; _uiState.value = _uiState.value.copy(isSpeaking = true, speakingMessageId = messageId); androidTTS.speak(text) { _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null) } }
    fun onStopSpeaking() { androidTTS.stop(); _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null) }

    fun onLikeMessage(id: String) { val l = _uiState.value.likedMessages.toMutableSet(); val d = _uiState.value.dislikedMessages.toMutableSet(); if (id in l) l.remove(id) else { l.add(id); d.remove(id) }; _uiState.value = _uiState.value.copy(likedMessages = l, dislikedMessages = d) }
    fun onDislikeMessage(id: String) { val l = _uiState.value.likedMessages.toMutableSet(); val d = _uiState.value.dislikedMessages.toMutableSet(); if (id in d) d.remove(id) else { d.add(id); l.remove(id) }; _uiState.value = _uiState.value.copy(likedMessages = l, dislikedMessages = d) }

    fun onTranslateMessage(messageId: String, text: String) { viewModelScope.launch { _uiState.value = _uiState.value.copy(isTranslating = true); val t = geminiRepository.translate(text, _uiState.value.translateLanguage); _uiState.value = _uiState.value.copy(translateResult = t, showTranslateDialog = true, isTranslating = false) } }
    fun onDismissTranslateDialog() { _uiState.value = _uiState.value.copy(showTranslateDialog = false, translateResult = null) }
    fun onUpdateTranslateLanguage(lang: String) { _uiState.value = _uiState.value.copy(translateLanguage = lang) }

    fun onNewChat() { currentGenerationJob?.cancel(); currentGenerationJob = null; lastUserPrompt = ""; lastImageUris = emptyList(); _uiState.value = ChatUiState() }
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

    override fun onCleared() {
        super.onCleared()
        streamingVoiceManager.destroy()
        deviceController.cleanup()
        voiceUIManager.remove()
        notificationManager.cancelAll()
        viewModelScope.launch { chatRepository.cleanupOrphanedMessages() }
    }
}
