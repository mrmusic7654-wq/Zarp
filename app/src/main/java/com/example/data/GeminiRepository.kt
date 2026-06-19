package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
                maxOutputTokens = 1024
            }
        )
    }

    suspend fun generateResponse(prompt: String, modelName: String): String =
        withContext(Dispatchers.IO) {
            val model = getModel(modelName)
                ?: return@withContext "⚠️ API key not set."
            try {
                val response = model.generateContent(content { text(prompt) })
                response.text?.trim() ?: "No response."
            } catch (e: Exception) {
                "Error: ${e.localizedMessage ?: "Try again."}"
            }
        }

    suspend fun generateResponseWithImage(
        prompt: String, imageUri: Uri, modelName: String
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName)
            ?: return@withContext "⚠️ API key not set."
        try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext "Couldn't read image."
            val response = model.generateContent(content {
                image(bitmap)
                text(prompt.ifBlank { "Describe this image." })
            })
            response.text?.trim() ?: "No analysis."
        } catch (e: Exception) {
            "Image error: ${e.localizedMessage ?: "Try again."}"
        }
    }
}
