package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.util.MarkdownFormatter
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository(private val context: Context) {

    private fun getModel(modelName: String): GenerativeModel? {
        val key = KeyManager.getApiKey(context)
        if (key == null) {
            Log.e("GeminiRepo", "API key is null!")
            return null
        }
        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048
            },
            systemInstruction = content {
                text(
                    """
                    You are a helpful, friendly AI assistant. Follow these rules strictly:
                    - Give clear, direct answers without excessive markdown formatting
                    - Use natural paragraph breaks, not markdown headers
                    - When listing items, use simple bullet points (•) not asterisks or dashes
                    - Keep code blocks clean and properly formatted
                    - Be concise but thorough
                    - No unnecessary **bold** or *italic* unless truly needed for clarity
                    - Write like a knowledgeable friend, not a textbook
                    """
                )
            }
        )
    }

    suspend fun generateResponse(prompt: String, modelName: String): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName)
        if (model == null) {
            return@withContext "⚠️ API key not set. Go to **Settings → API Key** to add your Gemini key."
        }
        try {
            val response = model.generateContent(content { text(prompt) })
            val rawText = response.text ?: "I couldn't generate a response. Please try again."
            MarkdownFormatter.clean(rawText)
        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error calling Gemini API", e)
            "I encountered an error. Please try again.\n\nError: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    suspend fun generateResponseWithImage(
        prompt: String,
        imageUri: Uri,
        modelName: String
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName)
        if (model == null) {
            return@withContext "⚠️ API key not set."
        }
        try {
            val bitmap: Bitmap? = try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                null
            }

            if (bitmap == null) {
                return@withContext "I couldn't process that image. Please try another."
            }

            val response = model.generateContent(
                content {
                    image(bitmap)  // Fixed: pass Bitmap directly
                    text(prompt.ifBlank { "Describe this image clearly and helpfully." })
                }
            )
            val rawText = response.text ?: "I couldn't analyze the image."
            MarkdownFormatter.clean(rawText)
        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error with image", e)
            "I couldn't process that image. Error: ${e.localizedMessage}"
        }
    }
}
