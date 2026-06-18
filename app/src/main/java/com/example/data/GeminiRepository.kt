package com.example.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository(apiKey: String) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",   // free tier
        apiKey = apiKey
    )

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val response = generativeModel.generateContent(
                content { text(prompt) }
            )
            response.text ?: "I couldn’t generate a response. Please try again."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Something went wrong"}"
        }
    }
}
