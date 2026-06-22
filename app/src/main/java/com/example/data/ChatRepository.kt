package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Conversation
import com.example.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ChatRepository(context: Context) {

    companion object {
        private const val TAG = "ChatRepo"
        private const val MAX_BACKUP_FILES = 5
    }

    // ── File paths ──
    private val conversationsFile = File(context.filesDir, "zarp_conversations.json")
    private val tasksFile = File(context.filesDir, "zarp_tasks.json")
    private val projectsFile = File(context.filesDir, "zarp_projects.json")
    private val messagesDir = File(context.filesDir, "zarp_messages")
    private val taskMessagesDir = File(context.filesDir, "zarp_task_messages")
    private val backupsDir = File(context.filesDir, "zarp_backups")

    // ── State flows ──
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: Flow<List<Conversation>> = _conversations.asStateFlow()

    private val _tasks = MutableStateFlow<List<Conversation>>(emptyList())
    val tasks: Flow<List<Conversation>> = _tasks.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    val projects: Flow<List<ProjectInfo>> = _projects.asStateFlow()

    init {
        messagesDir.mkdirs()
        taskMessagesDir.mkdirs()
        backupsDir.mkdirs()
        loadAll()
    }

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class ProjectInfo(
        val id: String, val name: String, val repoUrl: String,
        val repoOwner: String, val repoName: String, val filesCount: Int,
        val buildStatus: String?, val createdAt: Long, val lastUpdated: Long,
        val architectureType: String?, val totalFixAttempts: Int
    )

    data class TaskProgress(
        val taskId: String, val phase: String, val percentage: Float,
        val message: String, val timestamp: Long = System.currentTimeMillis()
    )

    // ═══════════════════════════════════════════
    // Load All (with backup recovery)
    // ═══════════════════════════════════════════

    private fun loadAll() {
        _conversations.value = loadJsonList(conversationsFile) { obj ->
            Conversation(obj.getString("id"), obj.getString("title"), obj.optString("dateGroup", "Today"), emptyList())
        }
        _tasks.value = loadJsonList(tasksFile) { obj ->
            Conversation(obj.getString("id"), obj.getString("title"), obj.optString("dateGroup", "Today"), emptyList())
        }
        _projects.value = loadJsonList(projectsFile) { obj ->
            ProjectInfo(
                obj.optString("id"), obj.optString("name"), obj.optString("repoUrl"),
                obj.optString("repoOwner"), obj.optString("repoName"), obj.optInt("filesCount"),
                obj.optString("buildStatus", null), obj.optLong("createdAt"), obj.optLong("lastUpdated"),
                obj.optString("architectureType", null), obj.optInt("totalFixAttempts")
            )
        }
        Log.d(TAG, "📂 Loaded: ${_conversations.value.size} chats, ${_tasks.value.size} tasks, ${_projects.value.size} projects")
    }

    private fun <T> loadJsonList(file: File, parser: (JSONObject) -> T): List<T> {
        return try {
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i -> parser(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            Log.e(TAG, "Load failed: ${file.name} — attempting backup recovery", e)
            recoverFromBackup(file)?.let { return loadJsonList(File(file.parent, file.name), parser) }
            emptyList()
        }
    }

    private fun <T> saveJsonList(file: File, items: List<T>, serializer: (T) -> JSONObject) {
        try {
            // Create backup first
            if (file.exists() && file.length() > 0) {
                val backup = File(backupsDir, "${file.name}.${System.currentTimeMillis()}.bak")
                file.copyTo(backup, overwrite = true)
                // Clean old backups
                val backups = backupsDir.listFiles()?.filter { it.name.startsWith(file.name) }?.sortedByDescending { it.lastModified() } ?: emptyList()
                backups.drop(MAX_BACKUP_FILES).forEach { it.delete() }
            }
            val arr = JSONArray()
            items.forEach { arr.put(serializer(it)) }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Save failed: ${file.name}", e)
        }
    }

    private fun recoverFromBackup(file: File): Boolean {
        return try {
            val backups = backupsDir.listFiles()?.filter { it.name.startsWith(file.name) }?.sortedByDescending { it.lastModified() } ?: emptyList()
            if (backups.isNotEmpty()) {
                backups.first().copyTo(file, overwrite = true)
                Log.d(TAG, "🔄 Recovered ${file.name} from backup")
                true
            } else false
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // Conversations
    // ═══════════════════════════════════════════

    fun getAllConversations(): Flow<List<Conversation>> = _conversations

    suspend fun getMessagesForConversationOnce(conversationId: String): List<Message> {
        return withContext(Dispatchers.IO) {
            loadMessages(getMessagesFile(conversationId), conversationId)
        }
    }

    suspend fun createNewConversation(firstMessage: String): Conversation {
        return withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val title = generateTitle(firstMessage)
            val conv = Conversation(id, title, "Today", emptyList())
            val list = _conversations.value.toMutableList(); list.add(0, conv); _conversations.value = list
            saveJsonList(conversationsFile, _conversations.value) { obj -> JSONObject().apply { put("id", obj.id); put("title", obj.title); put("dateGroup", obj.dateGroup) } }
            saveMessageToFile(id, Message(UUID.randomUUID().toString(), firstMessage, true, System.currentTimeMillis()), false)
            Log.d(TAG, "✅ Chat: $title")
            conv
        }
    }

    suspend fun addMessageToConversation(convId: String, text: String, isUser: Boolean) {
        withContext(Dispatchers.IO) {
            saveMessageToFile(convId, Message(UUID.randomUUID().toString(), text, isUser, System.currentTimeMillis()), false)
        }
    }

    suspend fun deleteConversation(id: String) {
        withContext(Dispatchers.IO) {
            getMessagesFile(id).delete()
            getTaskMessagesFile(id).delete()
            _conversations.value = _conversations.value.filter { it.id != id }
            saveJsonList(conversationsFile, _conversations.value) { obj -> JSONObject().apply { put("id", obj.id); put("title", obj.title); put("dateGroup", obj.dateGroup) } }
            _tasks.value = _tasks.value.filter { it.id != id }
            saveJsonList(tasksFile, _tasks.value) { obj -> JSONObject().apply { put("id", obj.id); put("title", obj.title); put("dateGroup", obj.dateGroup) } }
            _projects.value = _projects.value.filter { it.id != id }
            saveJsonList(projectsFile, _projects.value) { p -> JSONObject().apply { put("id", p.id); put("name", p.name); put("repoUrl", p.repoUrl); put("repoOwner", p.repoOwner); put("repoName", p.repoName); put("filesCount", p.filesCount); put("buildStatus", p.buildStatus ?: ""); put("createdAt", p.createdAt); put("lastUpdated", p.lastUpdated); put("architectureType", p.architectureType ?: ""); put("totalFixAttempts", p.totalFixAttempts) } }
        }
    }

    // ═══════════════════════════════════════════
    // Tasks
    // ═══════════════════════════════════════════

    fun getAllTasks(): Flow<List<Conversation>> = _tasks

    suspend fun getTaskMessagesOnce(taskId: String): List<Message> {
        return withContext(Dispatchers.IO) {
            val msgs = loadMessages(getTaskMessagesFile(taskId), taskId)
            if (msgs.isEmpty()) loadMessages(getMessagesFile(taskId), taskId) else msgs
        }
    }

    suspend fun saveConversationAsTask(conversationId: String, taskTitle: String) {
        withContext(Dispatchers.IO) {
            val source = getMessagesFile(conversationId)
            val dest = getTaskMessagesFile(conversationId)
            if (source.exists()) source.copyTo(dest, overwrite = true)

            val list = _tasks.value.toMutableList()
            val idx = list.indexOfFirst { it.id == conversationId }
            if (idx >= 0) list[idx] = list[idx].copy(title = taskTitle, dateGroup = "Today")
            else list.add(0, Conversation(conversationId, taskTitle, "Today", emptyList()))
            _tasks.value = list
            saveJsonList(tasksFile, _tasks.value) { obj -> JSONObject().apply { put("id", obj.id); put("title", obj.title); put("dateGroup", obj.dateGroup) } }
            Log.d(TAG, "🤖 Task: $taskTitle")
        }
    }

    suspend fun addTaskMessage(taskId: String, text: String, isUser: Boolean) {
        withContext(Dispatchers.IO) {
            saveMessageToFile(taskId, Message(UUID.randomUUID().toString(), text, isUser, System.currentTimeMillis()), true)
        }
    }

    suspend fun deleteTask(taskId: String) {
        withContext(Dispatchers.IO) {
            getTaskMessagesFile(taskId).delete()
            _tasks.value = _tasks.value.filter { it.id != taskId }
            saveJsonList(tasksFile, _tasks.value) { obj -> JSONObject().apply { put("id", obj.id); put("title", obj.title); put("dateGroup", obj.dateGroup) } }
        }
    }

    suspend fun updateTaskProgress(taskId: String, progress: TaskProgress) {
        withContext(Dispatchers.IO) {
            val file = File(taskMessagesDir, "${taskId}_progress.json")
            try {
                val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()
                val obj = JSONObject().apply { put("phase", progress.phase); put("percentage", progress.percentage.toDouble()); put("message", progress.message); put("timestamp", progress.timestamp) }
                arr.put(obj)
                file.writeText(arr.toString())
            } catch (e: Exception) { Log.e(TAG, "Progress save failed", e) }
        }
    }

    suspend fun getTaskProgress(taskId: String): List<TaskProgress> {
        return withContext(Dispatchers.IO) {
            val file = File(taskMessagesDir, "${taskId}_progress.json")
            if (!file.exists()) return@withContext emptyList()
            try {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    TaskProgress(taskId, obj.optString("phase"), obj.optDouble("percentage").toFloat(), obj.optString("message"), obj.optLong("timestamp"))
                }
            } catch (e: Exception) { emptyList() }
        }
    }

    // ═══════════════════════════════════════════
    // Projects
    // ═══════════════════════════════════════════

    fun getAllProjects(): Flow<List<ProjectInfo>> = _projects

    suspend fun saveProject(project: ProjectInfo) {
        withContext(Dispatchers.IO) {
            val list = _projects.value.toMutableList()
            val idx = list.indexOfFirst { it.id == project.id }
            if (idx >= 0) list[idx] = project else list.add(0, project)
            _projects.value = list
            saveJsonList(projectsFile, _projects.value) { p -> JSONObject().apply { put("id", p.id); put("name", p.name); put("repoUrl", p.repoUrl); put("repoOwner", p.repoOwner); put("repoName", p.repoName); put("filesCount", p.filesCount); put("buildStatus", p.buildStatus ?: ""); put("createdAt", p.createdAt); put("lastUpdated", p.lastUpdated); put("architectureType", p.architectureType ?: ""); put("totalFixAttempts", p.totalFixAttempts) } }
        }
    }

    suspend fun updateProjectBuildStatus(projectId: String, status: String?, fixAttempts: Int = 0) {
        withContext(Dispatchers.IO) {
            val list = _projects.value.toMutableList()
            val idx = list.indexOfFirst { it.id == projectId }
            if (idx >= 0) {
                list[idx] = list[idx].copy(buildStatus = status, totalFixAttempts = fixAttempts, lastUpdated = System.currentTimeMillis())
                _projects.value = list
                saveJsonList(projectsFile, _projects.value) { p -> JSONObject().apply { put("id", p.id); put("name", p.name); put("repoUrl", p.repoUrl); put("repoOwner", p.repoOwner); put("repoName", p.repoName); put("filesCount", p.filesCount); put("buildStatus", p.buildStatus ?: ""); put("createdAt", p.createdAt); put("lastUpdated", p.lastUpdated); put("architectureType", p.architectureType ?: ""); put("totalFixAttempts", p.totalFixAttempts) } }
            }
        }
    }

    suspend fun deleteProject(projectId: String) {
        withContext(Dispatchers.IO) {
            _projects.value = _projects.value.filter { it.id != projectId }
            saveJsonList(projectsFile, _projects.value) { p -> JSONObject().apply { put("id", p.id); put("name", p.name); put("repoUrl", p.repoUrl); put("repoOwner", p.repoOwner); put("repoName", p.repoName); put("filesCount", p.filesCount); put("buildStatus", p.buildStatus ?: ""); put("createdAt", p.createdAt); put("lastUpdated", p.lastUpdated); put("architectureType", p.architectureType ?: ""); put("totalFixAttempts", p.totalFixAttempts) } }
        }
    }

    // ═══════════════════════════════════════════
    // Shared Helpers
    // ═══════════════════════════════════════════

    private fun getMessagesFile(id: String) = File(messagesDir, "$id.json")
    private fun getTaskMessagesFile(id: String) = File(taskMessagesDir, "$id.json")

    private fun loadMessages(file: File, id: String): List<Message> {
        return try {
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            val msgs = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Message(obj.getString("id"), obj.getString("text"), obj.getBoolean("isUser"), obj.getLong("timestamp"))
            }
            if (msgs.isNotEmpty()) Log.d(TAG, "📂 $id: ${msgs.size} messages")
            msgs
        } catch (e: Exception) {
            Log.e(TAG, "Load messages failed: $id", e)
            emptyList()
        }
    }

    private fun saveMessageToFile(id: String, message: Message, isTask: Boolean) {
        try {
            val file = if (isTask) getTaskMessagesFile(id) else getMessagesFile(id)
            val arr = if (file.exists()) {
                try { JSONArray(file.readText()) } catch (e: Exception) { JSONArray() }
            } else JSONArray()
            val obj = JSONObject().apply { put("id", message.id); put("text", message.text); put("isUser", message.isUser); put("timestamp", message.timestamp) }
            arr.put(obj)
            file.writeText(arr.toString())
        } catch (e: Exception) { Log.e(TAG, "Save message failed", e) }
    }

    private fun generateTitle(text: String): String {
        val cleaned = text.replace(Regex("📎.*$"), "").replace(Regex("📷.*$"), "").replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length > 40) cleaned.take(40) + "..." else cleaned.ifBlank { "New chat" }
    }

    // ═══════════════════════════════════════════
    // Maintenance
    // ═══════════════════════════════════════════

    suspend fun cleanupOrphanedMessages() {
        withContext(Dispatchers.IO) {
            val allIds = (_conversations.value.map { it.id } + _tasks.value.map { it.id }).toSet()
            messagesDir.listFiles()?.forEach { file ->
                val id = file.nameWithoutExtension
                if (id !in allIds && !id.endsWith("_progress")) {
                    file.delete()
                    Log.d(TAG, "🧹 Cleaned orphan: $id")
                }
            }
        }
    }

    fun getStorageStats(): StorageStats {
        val convSize = conversationsFile.length()
        val taskSize = tasksFile.length()
        val projSize = projectsFile.length()
        val msgSize = messagesDir.listFiles()?.sumOf { it.length() } ?: 0
        val taskMsgSize = taskMessagesDir.listFiles()?.sumOf { it.length() } ?: 0
        val backupSize = backupsDir.listFiles()?.sumOf { it.length() } ?: 0
        return StorageStats(convSize, taskSize, projSize, msgSize, taskMsgSize, backupSize)
    }

    data class StorageStats(
        val conversationsBytes: Long, val tasksBytes: Long, val projectsBytes: Long,
        val messagesBytes: Long, val taskMessagesBytes: Long, val backupsBytes: Long
    ) {
        val totalBytes: Long get() = conversationsBytes + tasksBytes + projectsBytes + messagesBytes + taskMessagesBytes + backupsBytes
        val totalKB: Long get() = totalBytes / 1024
    }
}
