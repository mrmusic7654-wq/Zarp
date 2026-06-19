package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository(private val context: Context) {

    private fun getModel(modelName: String): GenerativeModel? {
        val key = KeyManager.getApiKey(context) ?: return null
        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 65536   // allow long answers for coding etc.
            },
            systemInstruction = content {
                text("""
You are Zarp, a friendly and capable AI assistant built on Gemini.
- Answer naturally, like a helpful expert.
- Keep short answers crisp; feel free to give longer, detailed explanations when the question needs it (e.g., coding, analysis).
- Use **bold** and *italic* sparingly for emphasis.
- Use bullet points (•) or numbered lists when listing items.
- Use triple backticks with language name for code blocks.
- Use tables when presenting structured data.
- When you need to reason step‑by‑step, wrap it in [THINKING]…[/THINKING] before your final answer.
- Be warm, direct, and enjoyable to chat with.
                """.trimIndent())
            }
        )
    }

    suspend fun generateResponse(prompt: String, modelName: String): String =
        withContext(Dispatchers.IO) {
            val model = getModel(modelName) ?: return@withContext "⚠️ API key not set."
            try {
                val response = model.generateContent(content { text(prompt) })
                response.text ?: "No response."
            } catch (e: Exception) {
                "Error: ${e.localizedMessage ?: "Try again."}"
            }
        }

    suspend fun generateResponseWithImage(
        prompt: String, imageUri: Uri, modelName: String
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName) ?: return@withContext "⚠️ API key not set."
        try {
            val bitmap: Bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext "Couldn't read image."
            val response = model.generateContent(content {
                image(bitmap)
                text(prompt.ifBlank { "Describe this image." })
            })
            response.text ?: "No analysis."
        } catch (e: Exception) {
            "Image error: ${e.localizedMessage ?: "Try again."}"
        }
    }
}
