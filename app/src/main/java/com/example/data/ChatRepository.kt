package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Conversation
import com.example.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ChatRepository(context: Context) {

    companion object {
        private const val TAG = "ChatRepo"
    }

    private val conversationsFile = File(context.filesDir, "zarp_conversations.json")
    private val tasksFile = File(context.filesDir, "zarp_tasks.json")
    private val messagesDir = File(context.filesDir, "zarp_messages")
    private val taskMessagesDir = File(context.filesDir, "zarp_task_messages")

    // ── Conversations ──
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: Flow<List<Conversation>> = _conversations.asStateFlow()

    // ── Tasks ──
    private val _tasks = MutableStateFlow<List<Conversation>>(emptyList())
    val tasks: Flow<List<Conversation>> = _tasks.asStateFlow()

    init {
        messagesDir.mkdirs()
        taskMessagesDir.mkdirs()
        loadConversations()
        loadTasks()
    }

    // ═══════════════════════════════════════════
    // Conversations
    // ═══════════════════════════════════════════

    private fun loadConversations() {
        try {
            if (!conversationsFile.exists()) return
            val arr = JSONArray(conversationsFile.readText())
            val list = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Conversation(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    dateGroup = obj.optString("dateGroup", "Today"),
                    messages = emptyList()
                ))
            }
            _conversations.value = list
            Log.d(TAG, "📂 Loaded ${list.size} conversations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversations", e)
        }
    }

    private fun saveConversations() {
        try {
            val arr = JSONArray()
            _conversations.value.forEach { conv ->
                val obj = JSONObject()
                obj.put("id", conv.id)
                obj.put("title", conv.title)
                obj.put("dateGroup", conv.dateGroup)
                arr.put(obj)
            }
            conversationsFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversations", e)
        }
    }

    fun getAllConversations(): Flow<List<Conversation>> = _conversations

    suspend fun getMessagesForConversationOnce(conversationId: String): List<Message> {
        return try {
            val file = getMessagesFile(conversationId)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            val list = mutableListOf<Message>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Message(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    isUser = obj.getBoolean("isUser"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
            Log.d(TAG, "📂 Loaded ${list.size} messages for conversation $conversationId")
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages for $conversationId", e)
            emptyList()
        }
    }

    suspend fun createNewConversation(firstMessage: String): Conversation {
        val conversationId = UUID.randomUUID().toString()
        val title = generateTitle(firstMessage)
        val conv = Conversation(id = conversationId, title = title, dateGroup = "Today", messages = emptyList())
        val list = _conversations.value.toMutableList()
        list.add(0, conv)
        _conversations.value = list
        saveConversations()

        saveMessageToFile(conversationId, Message(
            id = UUID.randomUUID().toString(), text = firstMessage, isUser = true, timestamp = System.currentTimeMillis()
        ), isTask = false)
        Log.d(TAG, "✅ Created conversation: $conversationId - $title")
        return conv
    }

    suspend fun addMessageToConversation(conversationId: String, text: String, isUser: Boolean) {
        val msg = Message(id = UUID.randomUUID().toString(), text = text, isUser = isUser, timestamp = System.currentTimeMillis())
        saveMessageToFile(conversationId, msg, isTask = false)
        Log.d(TAG, "✅ Added ${if (isUser) "user" else "AI"} message to $conversationId")
    }

    suspend fun deleteConversation(conversationId: String) {
        getMessagesFile(conversationId).delete()
        _conversations.value = _conversations.value.filter { it.id != conversationId }
        saveConversations()
        // Also remove from tasks if present
        getTaskMessagesFile(conversationId).delete()
        _tasks.value = _tasks.value.filter { it.id != conversationId }
        saveTasks()
        Log.d(TAG, "🗑️ Deleted conversation $conversationId")
    }

    // ═══════════════════════════════════════════
    // Tasks
    // ═══════════════════════════════════════════

    private fun loadTasks() {
        try {
            if (!tasksFile.exists()) return
            val arr = JSONArray(tasksFile.readText())
            val list = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Conversation(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    dateGroup = obj.optString("dateGroup", "Today"),
                    messages = emptyList()
                ))
            }
            _tasks.value = list
            Log.d(TAG, "🤖 Loaded ${list.size} tasks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tasks", e)
        }
    }

    private fun saveTasks() {
        try {
            val arr = JSONArray()
            _tasks.value.forEach { task ->
                val obj = JSONObject()
                obj.put("id", task.id)
                obj.put("title", task.title)
                obj.put("dateGroup", task.dateGroup)
                arr.put(obj)
            }
            tasksFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tasks", e)
        }
    }

    fun getAllTasks(): Flow<List<Conversation>> = _tasks

    suspend fun getTaskMessagesOnce(taskId: String): List<Message> {
        return try {
            val file = getTaskMessagesFile(taskId)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            val list = mutableListOf<Message>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Message(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    isUser = obj.getBoolean("isUser"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
            Log.d(TAG, "🤖 Loaded ${list.size} task messages for $taskId")
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load task messages for $taskId", e)
            emptyList()
        }
    }

    /**
     * Saves the current conversation as a task with its own isolated message storage.
     * Task messages are copied to a separate directory so they survive independently.
     */
    suspend fun saveConversationAsTask(conversationId: String, taskTitle: String) {
        // Copy messages from conversation storage to task storage
        val sourceFile = getMessagesFile(conversationId)
        val destFile = getTaskMessagesFile(conversationId)
        if (sourceFile.exists()) {
            sourceFile.copyTo(destFile, overwrite = true)
        }

        // Add to task list
        val taskList = _tasks.value.toMutableList()
        val existingIndex = taskList.indexOfFirst { it.id == conversationId }
        if (existingIndex >= 0) {
            taskList[existingIndex] = taskList[existingIndex].copy(title = taskTitle, dateGroup = "Today")
            Log.d(TAG, "🔄 Updated task: $taskTitle")
        } else {
            taskList.add(0, Conversation(id = conversationId, title = taskTitle, dateGroup = "Today", messages = emptyList()))
            Log.d(TAG, "🤖 Saved as task: $taskTitle")
        }
        _tasks.value = taskList
        saveTasks()
    }

    suspend fun addTaskMessage(taskId: String, text: String, isUser: Boolean) {
        val msg = Message(id = UUID.randomUUID().toString(), text = text, isUser = isUser, timestamp = System.currentTimeMillis())
        saveMessageToFile(taskId, msg, isTask = true)
        Log.d(TAG, "✅ Added ${if (isUser) "user" else "AI"} message to task $taskId")
    }

    suspend fun deleteTask(taskId: String) {
        getTaskMessagesFile(taskId).delete()
        _tasks.value = _tasks.value.filter { it.id != taskId }
        saveTasks()
        Log.d(TAG, "🗑️ Deleted task $taskId")
    }

    // ═══════════════════════════════════════════
    // Shared Helpers
    // ═══════════════════════════════════════════

    private fun getMessagesFile(conversationId: String): File {
        return File(messagesDir, "$conversationId.json")
    }

    private fun getTaskMessagesFile(taskId: String): File {
        return File(taskMessagesDir, "$taskId.json")
    }

    private fun saveMessageToFile(id: String, message: Message, isTask: Boolean) {
        try {
            val file = if (isTask) getTaskMessagesFile(id) else getMessagesFile(id)
            val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()
            val obj = JSONObject()
            obj.put("id", message.id)
            obj.put("text", message.text)
            obj.put("isUser", message.isUser)
            obj.put("timestamp", message.timestamp)
            arr.put(obj)
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message", e)
        }
    }

    private fun generateTitle(text: String): String {
        val cleaned = text
            .replace(Regex("📎.*$"), "")
            .replace(Regex("📷.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > 40) cleaned.take(40) + "..." else cleaned.ifBlank { "New chat" }
    }
}
