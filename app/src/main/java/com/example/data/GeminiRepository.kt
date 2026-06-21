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

    private val fullContextCount = 5
    private val embeddingManager = EmbeddingManager(context)

    private fun getModel(modelName: String, customStyle: String = "", useSearch: Boolean = false): GenerativeModel? {
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
- When using web search, cite sources briefly at the end like [Source: site.com].
- Be warm, direct, and enjoyable to chat with.
        """.trimIndent()

        val finalInstruction = if (customStyle.isNotBlank()) {
            "You are Zarp. $customStyle"
        } else {
            defaultInstruction
        }

        val config = com.google.ai.client.generativeai.type.generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 8192
        }

        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = config,
            systemInstruction = content { text(finalInstruction) },
            tools = if (useSearch) {
                listOf(
                    com.google.ai.client.generativeai.type.tool(
                        googleSearch = com.google.ai.client.generativeai.type.googleSearch()
                    )
                )
            } else {
                null
            }
        )
    }

    private suspend fun buildContext(history: List<Message>, currentPrompt: String): String {
        if (history.isEmpty()) return ""

        val relevant = embeddingManager.searchSimilar(currentPrompt, topK = 3)
        val relevantSet = relevant.toSet()

        val sb = StringBuilder()

        val recent = history.takeLast(fullContextCount)
        for (msg in recent) {
            val role = if (msg.isUser) "User" else "Zarp"
            sb.appendLine("$role: ${msg.text}")
        }

        val olderRelevant = history.filter { it.text in relevantSet && it !in recent }
        if (olderRelevant.isNotEmpty()) {
            sb.appendLine("\n--- Related earlier messages ---")
            for (msg in olderRelevant) {
                val role = if (msg.isUser) "User" else "Zarp"
                sb.appendLine("$role: ${msg.text}")
            }
            sb.appendLine("--- End related ---\n")
        }

        val rest = history.filter { it !in recent && it !in olderRelevant }
        if (rest.isNotEmpty()) {
            val userLines = rest.filter { it.isUser }.map { it.text }
            val assistantLines = rest.filter { !it.isUser }.map { it.text }
            if (userLines.isNotEmpty()) {
                sb.append("User previously asked about: ")
                sb.appendLine(userLines.joinToString("; ") { it.take(60) })
            }
            if (assistantLines.isNotEmpty()) {
                sb.append("Zarp previously answered: ")
                sb.appendLine(assistantLines.joinToString("; ") { it.take(60) })
            }
        }

        return sb.toString().trim()
    }

    suspend fun generateResponse(
        prompt: String,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = "",
        useSearch: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle, useSearch) ?: return@withContext "⚠️ API key not set."
        try {
            val contextBlock = buildContext(chatHistory, prompt)
            val response = model.generateContent(
                content {
                    if (contextBlock.isNotBlank()) text(contextBlock)
                    text(prompt)
                }
            )

            val rawText = response.text ?: "No response."

            // Extract grounding metadata if search was used
            if (useSearch) {
                val groundingChunks = response.rawResponse?.candidatesList
                    ?.firstOrNull()
                    ?.groundingMetadata
                    ?.groundingChunksList

                if (!groundingChunks.isNullOrEmpty()) {
                    val sources = groundingChunks.mapNotNull { chunk ->
                        chunk.web?.let { web ->
                            web.uri?.let { uri ->
                                web.title?.let { title ->
                                    "[Source: $title - $uri]"
                                } ?: "[Source: $uri]"
                            }
                        }
                    }.distinct().take(5)

                    if (sources.isNotEmpty()) {
                        val sourceText = sources.joinToString("\n")
                        "$rawText\n\n---\n🌐 *Searched ${sources.size} source(s):*\n$sourceText"
                    } else {
                        "$rawText\n\n---\n🌐 *Web search enabled*"
                    }
                } else {
                    rawText
                }
            } else {
                rawText
            }
        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error for $modelName: ${e.localizedMessage}", e)
            val msg = e.localizedMessage ?: ""
            when {
                msg.contains("403") -> "⚠️ Model '$modelName' not available on your plan. Try Gemini 2.5 Flash."
                msg.contains("404") -> "⚠️ Model '$modelName' not found."
                msg.contains("429") -> "⏳ Rate limit reached."
                msg.contains("503") || msg.contains("timeout", true) -> "⏰ Model busy. Try again."
                else -> "❌ Error: ${e.localizedMessage ?: "Try Gemini 2.5 Flash"}"
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

            val shortHistory = chatHistory.takeLast(3)
            val older = chatHistory.dropLast(3)
            val summary = if (older.isNotEmpty()) {
                val u = older.filter { it.isUser }.joinToString("; ") { it.text.take(60) }
                val a = older.filter { !it.isUser }.joinToString("; ") { it.text.take(60) }
                "Earlier: user [$u], Zarp [$a]."
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
            Log.e("GeminiRepo", "Image error: ${e.localizedMessage}", e)
            "❌ Image error: ${e.localizedMessage ?: "Try again."}"
        }
    }

    suspend fun translate(text: String, targetLang: String): String = withContext(Dispatchers.IO) {
        val key = KeyManager.getGeminiKey(context) ?: return@withContext "⚠️ API key not set."
        val model = GenerativeModel(
            modelName = "models/gemini-3.5-flash",
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.2f
                maxOutputTokens = 2048
            }
        )
        try {
            val response = model.generateContent(
                content {
                    text("Translate the following text to $targetLang. Return only the translated text, nothing else.\n\n$text")
                }
            )
            response.text ?: "Translation failed."
        } catch (e: Exception) {
            "❌ Translation error: ${e.localizedMessage ?: "Try again."}"
        }
    }

    suspend fun storeMessageEmbedding(text: String) {
        embeddingManager.embedAndStore(text)
    }
}
