package com.example.model

data class Message(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)
