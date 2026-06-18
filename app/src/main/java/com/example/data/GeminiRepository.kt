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
            Log.e("GeminiRepo", "❌ API key is null!")
            return null
        }
        Log.d("GeminiRepo", "🔑 Creating model: $modelName with key: ${key.take(6)}...")
        
        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 4096
            },
            systemInstruction = content {
                text(
                    """
                    You are Zarp, a highly capable and friendly AI assistant built on Gemini.

                    🎯 RESPONSE STYLE RULES:
                    - Write clear, natural responses with proper paragraph breaks
                    - Use emojis naturally for visual appeal (✅ ❌ 💡 📊 🔥 ⭐)
                    - Keep paragraphs short — 2-3 sentences maximum
                    - Use bold for important terms, italic for emphasis
                    - NEVER use markdown headers (##, ###, etc.)
                    - NEVER overuse bold/italic — only for key points

                    📋 LISTS:
                    - Use clean bullet points (•) for unordered lists
                    - Use numbered lists (1. 2. 3.) for sequences/steps
                    - Add a blank line before and after lists

                    📊 TABLES:
                    When data is best shown in a table, use:
                    | Column 1 | Column 2 | Column 3 |
                    |----------|----------|----------|
                    | Data     | Data     | Data     |

                    💻 CODE:
                    - Always wrap code in triple backticks with language: ```kotlin
                    - For inline code use single backticks: `code`
                    - Add brief explanation before code blocks

                    🧠 DEEP REASONING:
                    When a question requires step-by-step reasoning, use:
                    [THINKING]
                    Your detailed step-by-step thought process here...
                    [/THINKING]
                    Then provide the final answer.

                    ✨ GENERAL:
                    - Be conversational, like a knowledgeable friend
                    - Give direct answers first, then elaborate if needed
                    - If unsure, say: "I don't have live data to verify this, but based on my knowledge..."
                    - Use examples to illustrate complex points
                    - Keep responses organized and scannable

                    🚫 AVOID:
                    - Walls of text without breaks
                    - Excessive markdown formatting
                    - Academic/robotic tone
                    - Unnecessary disclaimers
                    - Repetitive phrases
                    """.trimIndent()
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
            Log.d("GeminiRepo", "📤 Sending prompt to $modelName...")
            val response = model.generateContent(content { text(prompt) })
            val rawText = response.text ?: "I couldn't generate a response. Please try again."
            val cleanedText = MarkdownFormatter.clean(rawText)
            Log.d("GeminiRepo", "📥 Response received: ${cleanedText.take(80)}...")
            cleanedText
        } catch (e: Exception) {
            Log.e("GeminiRepo", "❌ Error calling Gemini API", e)
            when {
                e.localizedMessage?.contains("403") == true -> 
                    "⚠️ This model is not available on your free plan. Try switching to Gemini 2.5 Flash in the model selector."
                e.localizedMessage?.contains("429") == true -> 
                    "⏳ Rate limit reached. Please wait a moment and try again."
                e.localizedMessage?.contains("400") == true -> 
                    "⚠️ Invalid request. Please try rephrasing your message."
                else -> "❌ Error: ${e.localizedMessage ?: "Something went wrong. Please try again."}"
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
            Log.d("GeminiRepo", "📸 Processing image with prompt: ${prompt.ifBlank { "No prompt" }}")
            
            val bitmap: Bitmap? = try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.e("GeminiRepo", "❌ Error reading image", e)
                null
            }

            if (bitmap == null) {
                return@withContext "❌ I couldn't process that image. Please try another one."
            }

            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(prompt.ifBlank { "Describe this image clearly and helpfully. What do you see? Provide useful insights about the content." })
                }
            )
            val rawText = response.text ?: "I couldn't analyze the image. Please try with a clearer photo."
            val cleanedText = MarkdownFormatter.clean(rawText)
            Log.d("GeminiRepo", "📥 Image response: ${cleanedText.take(80)}...")
            cleanedText
        } catch (e: Exception) {
            Log.e("GeminiRepo", "❌ Error with image generation", e)
            when {
                e.localizedMessage?.contains("403") == true -> 
                    "⚠️ This model doesn't support image analysis on the free plan. Try Gemini 2.5 Flash."
                e.localizedMessage?.contains("400") == true -> 
                    "⚠️ The image format is not supported. Please try a JPEG or PNG image."
                else -> "❌ Error processing image: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }
}
