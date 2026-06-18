package com.example.data

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository(private val context: Context) {

    private fun getModel(modelName: String): GenerativeModel? {
        val key = KeyManager.getApiKey(context) ?: return null
        return GenerativeModel(
            modelName = modelName,      // e.g., "gemini-3.0-flash-latest"
            apiKey = key
        )
    }

    suspend fun generateResponse(prompt: String, modelName: String): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName)
        if (model == null) {
            return@withContext "⚠️ API key not set. Go to **Settings → API Key** to add your Gemini key."
        }
        try {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: "I couldn’t generate a response. Please try again."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Something went wrong"}"
        }
    }
}
