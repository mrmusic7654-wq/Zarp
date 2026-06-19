package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.model.Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository(private val context: Context) {

    private val fullContextCount = 5   // keep last 5 messages in full
    private val maxSummaryAge = 30     // summarise anything older than the last 30

    private fun getModel(modelName: String, customStyle: String = ""): GenerativeModel? {
        val key = KeyManager.getGeminiKey(context) ?: return null

        val defaultInstruction = """
You are Zarp, a friendly and capable AI assistant built on Gemini.
- Answer naturally, like a helpful expert.
- Keep short answers crisp; give detailed explanations when needed (coding, analysis).
- Use **bold** and *italic* sparingly for emphasis.
- Use bullet points (•) or numbered lists when listing items.
- Use triple backticks with language name for code blocks.
- Use tables when presenting structured data.
- When reasoning step‑by‑step, wrap it in [THINKING]…[/THINKING] before your final answer.
- Be warm, direct, and enjoyable to chat with.
        """.trimIndent()

        val finalInstruction = if (customStyle.isNotBlank()) {
            "You are Zarp. $customStyle"
        } else {
            defaultInstruction
        }

        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 8192
            },
            systemInstruction = content { text(finalInstruction) }
        )
    }

    /**
     * Build a compact context string:
     * - Full text for the last [fullContextCount] messages.
     * - A one‑paragraph summary of older messages.
     */
    private fun buildContext(history: List<Message>): String {
        if (history.isEmpty()) return ""

        val sb = StringBuilder()

        // ── recent messages (full text) ──
        val recent = history.takeLast(fullContextCount)
        for (msg in recent) {
            val role = if (msg.isUser) "User" else "Zarp"
            sb.appendLine("$role: ${msg.text}")
        }

        // ── older messages (summarised) ──
        val older = history.dropLast(fullContextCount)
        if (older.isNotEmpty()) {
            sb.appendLine("\n--- Earlier conversation summary ---")
            // Summarise user and assistant separately
            val userLines = older.filter { it.isUser }.map { it.text }
            val assistantLines = older.filter { !it.isUser }.map { it.text }

            if (userLines.isNotEmpty()) {
                sb.append("User asked about: ")
                sb.appendLine(userLines.joinToString("; ") { it.take(80) })
            }
            if (assistantLines.isNotEmpty()) {
                sb.append("Zarp answered with: ")
                sb.appendLine(assistantLines.joinToString("; ") { it.take(80) })
            }
            sb.appendLine("--- End of summary ---\n")
        }

        return sb.toString().trim()
    }

    suspend fun generateResponse(
        prompt: String,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = ""
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle) ?: return@withContext "⚠️ API key not set."
        try {
            val contextBlock = buildContext(chatHistory)

            val response = model.generateContent(
                content {
                    if (contextBlock.isNotBlank()) {
                        text(contextBlock)
                    }
                    text(prompt)
                }
            )
            response.text ?: "No response."
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            when {
                msg.contains("MAX_TOKENS") -> "⚠️ Response too long. Try a shorter question."
                msg.contains("403") -> "⚠️ Model not available on your plan."
                msg.contains("429") -> "⏳ Rate limit reached. Wait a moment."
                else -> "Error: ${e.localizedMessage ?: "Try again."}"
            }
        }
    }

    suspend fun generateResponseWithImage(
        prompt: String,
        imageUri: Uri,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = ""
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle) ?: return@withContext "⚠️ API key not set."
        try {
            val bitmap: Bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext "Couldn't read image."

            // Images use many tokens → keep only the last 3 messages + summary
            val shortHistory = chatHistory.takeLast(3)
            val older = chatHistory.dropLast(3)
            val summary = if (older.isNotEmpty()) {
                val userQ = older.filter { it.isUser }.joinToString("; ") { it.text.take(60) }
                val assistantA = older.filter { !it.isUser }.joinToString("; ") { it.text.take(60) }
                "Earlier: user asked [$userQ], Zarp answered [$assistantA]."
            } else ""

            val response = model.generateContent(
                content {
                    if (summary.isNotBlank()) text(summary)
                    shortHistory.forEach { msg ->
                        if (msg.isUser) text("User: ${msg.text}")
                        else text("Zarp: ${msg.text}")
                    }
                    image(bitmap)
                    text(prompt.ifBlank { "Describe this image." })
                }
            )
            response.text ?: "No analysis."
        } catch (e: Exception) {
            "Image error: ${e.localizedMessage ?: "Try again."}"
        }
    }
}
