package com.example.data

import com.example.data.local.ChatDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.model.Conversation
import com.example.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepository(private val database: ChatDatabase) {

    private val dao = database.conversationDao()

    fun getAllConversations(): Flow<List<Conversation>> {
        return dao.getAllConversations().map { entities ->
            entities.map { it.toConversation() }
        }
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<Message>> {
        return dao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toMessage() }
        }
    }

    suspend fun getMessagesForConversationOnce(conversationId: String): List<Message> {
        return dao.getMessagesForConversationOnce(conversationId).map { it.toMessage() }
    }

    suspend fun createNewConversation(firstMessage: String): Conversation {
        val conversationId = UUID.randomUUID().toString()
        val title = if (firstMessage.length > 40) firstMessage.take(40) + "..." else firstMessage
        val now = System.currentTimeMillis()

        val conversation = ConversationEntity(
            id = conversationId,
            title = title,
            dateGroup = "Today",
            lastUpdated = now
        )
        dao.insertConversation(conversation)

        val userMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            text = firstMessage,
            isUser = true,
            timestamp = now
        )
        dao.insertMessage(userMessage)

        return conversation.toConversation()
    }

    suspend fun addMessageToConversation(conversationId: String, text: String, isUser: Boolean) {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            text = text,
            isUser = isUser,
            timestamp = System.currentTimeMillis()
        )
        dao.insertMessage(message)

        val conv = dao.getConversationById(conversationId)
        if (conv != null) {
            dao.insertConversation(conv.copy(lastUpdated = System.currentTimeMillis()))
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        dao.deleteConversationWithMessages(conversationId)
    }

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        val conv = dao.getConversationById(conversationId)
        if (conv != null) {
            dao.insertConversation(conv.copy(title = title))
        }
    }

    private fun ConversationEntity.toConversation() = Conversation(
        id = id,
        title = title,
        dateGroup = dateGroup,
        messages = emptyList()
    )

    private fun MessageEntity.toMessage() = Message(
        id = id,
        text = text,
        isUser = isUser,
        timestamp = timestamp
    )
}
