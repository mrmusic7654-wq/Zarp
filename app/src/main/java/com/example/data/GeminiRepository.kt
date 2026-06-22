package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.search.WebSearchManager
import com.example.model.Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GeminiRepository(private val context: Context) {

    companion object {
        private const val TAG = "GeminiRepo"
        private const val MAX_OUTPUT_TOKENS = 8192
        private const val IMAGE_MAX_DIMENSION = 2048
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 1000L
    }

    private val fullContextCount = 5
    private val embeddingManager = EmbeddingManager(context)

    // ═══════════════════════════════════════════
    // Model Factory with Retry
    // ═══════════════════════════════════════════

    private fun getModel(modelName: String, customStyle: String = ""): GenerativeModel? {
        val key = KeyManager.getGeminiKey(context)
        if (key.isNullOrBlank()) {
            Log.e(TAG, "❌ Gemini API key is null or empty")
            return null
        }
        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = MAX_OUTPUT_TOKENS
            },
            systemInstruction = content { text(buildSystemPrompt(customStyle)) }
        )
    }

    private fun buildSystemPrompt(customStyle: String): String {
        if (customStyle.isNotBlank()) return "You are Zarp. $customStyle"
        return """
You are Zarp — an elite, highly intelligent AI assistant.

🎯 RESPONSE RULES:
- Lead with the direct answer in 1-2 sentences
- Keep paragraphs short (2-3 sentences max)
- Use **bold** only for 2-3 key terms per response
- Use • bullets and 1. numbered lists naturally
- Use ```language for code blocks with language tag
- Use markdown tables for comparisons
- Use [THINKING]...[/THINKING] for step-by-step reasoning

🌐 SOURCE CITATIONS (web search):
[Source: Page Title](https://url.com)
Each source on its own line at the end. NEVER use inline (url).

🚫 NEVER:
- Markdown headers (##, ###)
- Inline URLs for sources
- Walls of text without breaks
- "As an AI language model..."
- Fabricated facts or URLs

Be warm, direct, and helpful. Answer like a knowledgeable friend.
        """.trimIndent()
    }

    // ═══════════════════════════════════════════
    // Context Builder
    // ═══════════════════════════════════════════

    private suspend fun buildContext(history: List<Message>, currentPrompt: String): String {
        if (history.isEmpty()) return ""
        val sb = StringBuilder()
        val recent = history.takeLast(fullContextCount)
        for (msg in recent) {
            sb.appendLine("${if (msg.isUser) "User" else "Zarp"}: ${msg.text}")
        }
        val rest = history.dropLast(fullContextCount)
        if (rest.isNotEmpty()) {
            val u = rest.filter { it.isUser }.map { it.text.take(60) }.distinct().joinToString("; ")
            val a = rest.filter { !it.isUser }.map { it.text.take(60) }.distinct().joinToString("; ")
            if (u.isNotBlank()) sb.appendLine("User asked: $u")
            if (a.isNotBlank()) sb.appendLine("Zarp answered: $a")
        }
        return sb.toString().trim()
    }

    // ═══════════════════════════════════════════
    // Generate Response with Retry
    // ═══════════════════════════════════════════

    suspend fun generateResponse(
        prompt: String,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = "",
        useSearch: Boolean = false
    ): String {
        return withRetry("generate") {
            if (useSearch) generateWithSearch(prompt, modelName, chatHistory, customStyle)
            else generateBasic(prompt, modelName, chatHistory, customStyle)
        }
    }

    private suspend fun generateBasic(
        prompt: String, modelName: String, chatHistory: List<Message>, customStyle: String
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle)
            ?: return@withContext "⚠️ API key not set. Go to Settings → API Keys."

        val contextBlock = buildContext(chatHistory, prompt)
        val response = model.generateContent(content {
            if (contextBlock.isNotBlank()) text(contextBlock)
            text(prompt)
        })

        val text = response.text
        if (text.isNullOrBlank()) return@withContext "⚠️ Empty response. Please try again."
        text
    }

    private suspend fun generateWithSearch(
        prompt: String, modelName: String, chatHistory: List<Message>, customStyle: String
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle)
            ?: return@withContext "⚠️ API key not set."

        val searchContext = try {
            WebSearchManager.searchForContext(prompt, context)
        } catch (e: Exception) { "" }

        val historyBlock = buildContext(chatHistory, prompt)
        val fullPrompt = buildString {
            if (searchContext.isNotBlank()) {
                appendLine(searchContext)
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("Cite sources as: [Source: Title](https://url)")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            }
            if (historyBlock.isNotBlank()) appendLine(historyBlock)
            appendLine("User: $prompt")
        }

        val response = model.generateContent(content { text(fullPrompt) })
        response.text ?: "⚠️ No response."
    }

    // ═══════════════════════════════════════════
    // Image Response
    // ═══════════════════════════════════════════

    suspend fun generateResponseWithImage(
        prompt: String, imageUri: Uri, modelName: String,
        chatHistory: List<Message> = emptyList(), customStyle: String = ""
    ): String = withRetry("image") {
        withContext(Dispatchers.IO) {
            val model = getModel(modelName, customStyle)
                ?: return@withContext "⚠️ API key not set."

            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext "❌ Cannot read image. Try JPEG or PNG."

            val processed = if (bitmap.width > IMAGE_MAX_DIMENSION || bitmap.height > IMAGE_MAX_DIMENSION) {
                val ratio = minOf(IMAGE_MAX_DIMENSION.toFloat() / bitmap.width, IMAGE_MAX_DIMENSION.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap

            val shortHistory = chatHistory.takeLast(3)
            val older = chatHistory.dropLast(3)
            val summary = if (older.isNotEmpty()) {
                val u = older.filter { it.isUser }.joinToString("; ") { it.text.take(60) }
                val a = older.filter { !it.isUser }.joinToString("; ") { it.text.take(60) }
                "Earlier: $u → $a"
            } else ""

            val response = model.generateContent(content {
                if (summary.isNotBlank()) text(summary)
                shortHistory.forEach { if (it.isUser) text("User: ${it.text}") else text("Zarp: ${it.text}") }
                image(processed)
                text(prompt.ifBlank { "Describe this image in detail." })
            })
            response.text ?: "⚠️ Could not analyze image."
        }
    }

    // ═══════════════════════════════════════════
    // Translate
    // ═══════════════════════════════════════════

    suspend fun translate(text: String, targetLang: String): String = withRetry("translate") {
        withContext(Dispatchers.IO) {
            val key = KeyManager.getGeminiKey(context) ?: return@withContext "⚠️ API key not set."
            val model = GenerativeModel(modelName = "models/gemini-3.5-flash", apiKey = key,
                generationConfig = com.google.ai.client.generativeai.type.generationConfig { temperature = 0.2f; maxOutputTokens = 2048 })
            val response = model.generateContent(content { text("Translate to $targetLang. Return ONLY translation.\n\n$text") })
            response.text ?: "Translation failed."
        }
    }

    // ═══════════════════════════════════════════
    // Embedding
    // ═══════════════════════════════════════════

    suspend fun storeMessageEmbedding(text: String) {
        if (text.isNotBlank()) embeddingManager.embedAndStore(text)
    }

    // ═══════════════════════════════════════════
    // Retry Logic
    // ═══════════════════════════════════════════

    private suspend fun <T> withRetry(operation: String, block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "⚠️ $operation attempt ${attempt + 1} failed: ${e.localizedMessage}")
                if (attempt < MAX_RETRIES - 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
        }
        throw lastError ?: Exception("$operation failed after $MAX_RETRIES attempts")
    }

    // ═══════════════════════════════════════════
    // Error Handler
    // ═══════════════════════════════════════════

    private fun handleApiError(e: Exception, modelName: String): String {
        val msg = e.localizedMessage ?: ""
        return when {
            msg.contains("403") -> "🔒 Model unavailable. Switch to Gemini 2.5 Flash."
            msg.contains("429") -> "⏳ Rate limited. Wait 30 seconds."
            msg.contains("503") || msg.contains("timeout", true) -> "⏰ Service busy. Try again."
            else -> "❌ ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}
