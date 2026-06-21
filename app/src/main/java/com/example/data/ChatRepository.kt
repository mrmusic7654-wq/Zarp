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
            if (!file.exists()) {
                Log.d("ChatRepo", "No messages file for $conversationId")
                return emptyList()
            }
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
        val title = if (firstMessage.length > 40) firstMessage.take(40) + "..." else firstMessage
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

        // Save the first message
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            text = firstMessage,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        saveMessageToFile(conversationId, userMessage)
        Log.d("ChatRepo", "✅ Created conversation: $conversationId - $title")
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
        Log.d("ChatRepo", "✅ Added ${if (isUser) "user" else "AI"} message to $conversationId")
    }

    private fun saveMessageToFile(conversationId: String, message: Message) {
        try {
            val file = getMessagesFile(conversationId)
            val arr = if (file.exists()) {
                JSONArray(file.readText())
            } else {
                JSONArray()
            }
            val obj = JSONObject()
            obj.put("id", message.id)
            obj.put("text", message.text)
            obj.put("isUser", message.isUser)
            obj.put("timestamp", message.timestamp)
            arr.put(obj)
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e("ChatRepo", "Failed to save message", e)
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        getMessagesFile(conversationId).delete()
        val list = _conversations.value.toMutableList()
        list.removeAll { it.id == conversationId }
        _conversations.value = list
        saveConversations()
        Log.d("ChatRepo", "🗑️ Deleted conversation $conversationId")
    }
}
