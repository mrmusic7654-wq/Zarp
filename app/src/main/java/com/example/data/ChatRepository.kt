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

    private val conversationsFile = File(context.filesDir, "zarp_conversations.json")
    private val messagesDir = File(context.filesDir, "zarp_messages")

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: Flow<List<Conversation>> = _conversations.asStateFlow()

    init {
        messagesDir.mkdirs()
        loadConversations()
    }

    private fun loadConversations() {
        try {
            if (!conversationsFile.exists()) return
            val json = conversationsFile.readText()
            val arr = JSONArray(json)
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
            Log.d("ChatRepo", "📂 Loaded ${list.size} conversations")
        } catch (e: Exception) {
            Log.e("ChatRepo", "Failed to load conversations", e)
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
            Log.e("ChatRepo", "Failed to save conversations", e)
        }
    }

    private fun getMessagesFile(conversationId: String): File {
        return File(messagesDir, "$conversationId.json")
    }

    fun getAllConversations(): Flow<List<Conversation>> = _conversations

    suspend fun getMessagesForConversationOnce(conversationId: String): List<Message> {
        return try {
            val file = getMessagesFile(conversationId)
            if (!file.exists()) return emptyList()
            val json = file.readText()
            val arr = JSONArray(json)
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
            Log.d("ChatRepo", "📂 Loaded ${list.size} messages for $conversationId")
            list
        } catch (e: Exception) {
            Log.e("ChatRepo", "Failed to load messages", e)
            emptyList()
        }
    }

    suspend fun createNewConversation(firstMessage: String): Conversation {
        val conversationId = UUID.randomUUID().toString()
        val title = generateTitle(firstMessage)
        val conv = Conversation(
            id = conversationId,
            title = title,
            dateGroup = "Today",
            messages = emptyList()
        )
        val list = _conversations.value.toMutableList()
        list.add(0, conv)
        _conversations.value = list
        saveConversations()

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            text = firstMessage,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        saveMessageToFile(conversationId, userMessage)
        Log.d("ChatRepo", "✅ Created: $conversationId - $title")
        return conv
    }

    suspend fun addMessageToConversation(conversationId: String, text: String, isUser: Boolean) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            text = text,
            isUser = isUser,
            timestamp = System.currentTimeMillis()
        )
        saveMessageToFile(conversationId, message)

        // Auto‑update title if it's still generic
        val conv = _conversations.value.find { it.id == conversationId }
        if (conv != null && (conv.title == "New chat" || conv.title.startsWith("📷") || conv.title.startsWith("📎"))) {
            val messages = getMessagesForConversationOnce(conversationId)
            if (messages.size >= 2) {
                val newTitle = generateTitle(messages.firstOrNull { it.isUser }?.text ?: text)
                updateConversationTitle(conversationId, newTitle)
            }
        }
    }

    private fun updateConversationTitle(id: String, title: String) {
        val list = _conversations.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(title = title)
            _conversations.value = list
            saveConversations()
        }
    }

    fun updateConversationTitle(conversationId: String, newTitle: String) {
        updateConversationTitle(conversationId, newTitle)
    }

    private fun generateTitle(text: String): String {
        val cleaned = text
            .replace(Regex("📎.*$"), "")
            .replace(Regex("📷.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > 40) cleaned.take(40) + "..." else cleaned.ifBlank { "New chat" }
    }

    suspend fun deleteConversation(conversationId: String) {
        getMessagesFile(conversationId).delete()
        val list = _conversations.value.toMutableList()
        list.removeAll { it.id == conversationId }
        _conversations.value = list
        saveConversations()
        Log.d("ChatRepo", "🗑️ Deleted $conversationId")
    }

    fun searchConversations(query: String): List<Conversation> {
        if (query.isBlank()) return _conversations.value
        val q = query.lowercase()
        return _conversations.value.filter { conv ->
            conv.title.lowercase().contains(q) || conv.id.lowercase().contains(q)
        }
    }

    fun getConversationCount(): Int = _conversations.value.size
    fun getTotalMessageCount(): Int = messagesDir.listFiles()?.sumOf { file ->
        try { JSONArray(file.readText()).length() } catch (e: Exception) { 0 }
    } ?: 0
}
