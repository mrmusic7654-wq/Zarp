package com.example.model

data class Conversation(
    val id: String,
    val title: String,
    val dateGroup: String, // "Today", "Yesterday", "Previous 7 days", "Older"
    val messages: List<Message>
)
