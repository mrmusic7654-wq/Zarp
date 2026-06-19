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
        Log.d("GeminiRepo", "Creating model: $modelName")

        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048   // faster, still enough for most answers
            }
            // No system instruction for speed
        )
    }

    suspend fun generateResponse(prompt: String, modelName: String): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName)
        if (model == null) {
            return@withContext "⚠️ API key not set. Go to **Settings → API Key** to add your Gemini key."
        }
        try {
            Log.d("GeminiRepo", "Sending prompt to $modelName...")
            val response = model.generateContent(content { text(prompt) })
            val rawText = response.text ?: "I couldn't generate a response. Please try again."
            val cleanedText = MarkdownFormatter.clean(rawText)
            Log.d("GeminiRepo", "Response received: ${cleanedText.take(80)}...")
            cleanedText
        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error calling Gemini API", e)
            val errorMsg = e.localizedMessage ?: ""
            when {
                errorMsg.contains("403") ->
                    "⚠️ This model is not available on your free plan. Try switching to Gemini 2.5 Flash."
                errorMsg.contains("429") ->
                    "⏳ Rate limit reached. Please wait a moment and try again."
                errorMsg.contains("400") ->
                    "⚠️ Invalid request. Please try rephrasing your message."
                else ->
                    "❌ Error: ${e.localizedMessage ?: "Something went wrong. Please try again."}"
            }
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
            Log.d("GeminiRepo", "Processing image...")

            val bitmap: Bitmap? = try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.e("GeminiRepo", "Error reading image", e)
                null
            }

            if (bitmap == null) {
                return@withContext "❌ I couldn't process that image. Please try another one."
            }

            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(prompt.ifBlank { "Describe this image clearly and helpfully." })
                }
            )
            val rawText = response.text ?: "I couldn't analyze the image."
            val cleanedText = MarkdownFormatter.clean(rawText)
            Log.d("GeminiRepo", "Image response: ${cleanedText.take(80)}...")
            cleanedText
        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error with image", e)
            val errorMsg = e.localizedMessage ?: ""
            when {
                errorMsg.contains("403") ->
                    "⚠️ This model doesn't support image analysis on the free plan. Try Gemini 2.5 Flash."
                errorMsg.contains("400") ->
                    "⚠️ The image format is not supported. Please try a JPEG or PNG image."
                else ->
                    "❌ Error processing image: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }
}
