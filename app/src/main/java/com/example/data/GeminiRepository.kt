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

class GeminiRepository(private val context: Context) {

    companion object {
        private const val TAG = "GeminiRepo"
        private const val MAX_OUTPUT_TOKENS = 8192
        private const val IMAGE_MAX_DIMENSION = 2048
        private const val MAX_FILE_CHARS = 4000
    }

    private val fullContextCount = 5
    private val embeddingManager = EmbeddingManager(context)

    // ═══════════════════════════════════════════
    // Model Factory
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
        return if (customStyle.isNotBlank()) {
            "You are Zarp. $customStyle"
        } else {
            """
You are Zarp — a highly capable, warm, and intelligent AI assistant.

🎯 CORE BEHAVIOR:
- Be direct first, elaborate second. Answer the question, then explain.
- Match your tone to the user's vibe — casual or professional.
- Never sound robotic. Write like a brilliant friend.
- When given web search results, cite sources using [Source: title](url) format.

📝 FORMATTING:
- Use **bold** for key terms only — 2-3 times max per response.
- Use *italic* for light emphasis.
- Break text into scannable paragraphs (2-3 lines each).
- Use • bullet lists for options or features.
- Use 1. 2. 3. numbered lists for steps or sequences.

💻 CODE:
- Always wrap code in ```language ... ```
- Explain what the code does BEFORE showing it.
- For inline code references, use single backticks: `functionName()`

📊 DATA:
- Use markdown tables when comparing things.
- Add a blank line before and after tables.

🧠 DEEP REASONING:
- For complex problems, show your work:
  [THINKING]
  Step-by-step reasoning here...
  [/THINKING]
  Then give the final polished answer.

✨ STYLE:
- Use relevant emojis naturally (1-2 per paragraph max).
- End with a helpful follow-up question or suggestion.
- If unsure, say so clearly — never fabricate.
            """.trimIndent()
        }
    }

    // ═══════════════════════════════════════════
    // Context Builder
    // ═══════════════════════════════════════════

    private suspend fun buildContext(history: List<Message>, currentPrompt: String): String {
        if (history.isEmpty()) return ""

        val relevant = embeddingManager.searchSimilar(currentPrompt, topK = 3)
        val relevantSet = relevant.toSet()
        val sb = StringBuilder()

        val recent = history.takeLast(fullContextCount)
        for (msg in recent) {
            val role = if (msg.isUser) "👤 User" else "🤖 Zarp"
            sb.appendLine("$role: ${msg.text}")
        }

        val olderRelevant = history.filter { it.text in relevantSet && it !in recent }
        if (olderRelevant.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("── Related earlier context ──")
            for (msg in olderRelevant) {
                val role = if (msg.isUser) "User" else "Zarp"
                sb.appendLine("$role: ${msg.text.take(200)}")
            }
            sb.appendLine("── End related context ──")
            sb.appendLine()
        }

        val rest = history.filter { it !in recent && it !in olderRelevant }
        if (rest.isNotEmpty()) {
            val userTopics = rest.filter { it.isUser }
                .map { it.text.take(60) }.distinct().joinToString("; ")
            val aiTopics = rest.filter { !it.isUser }
                .map { it.text.take(60) }.distinct().joinToString("; ")
            if (userTopics.isNotBlank()) sb.appendLine("📋 User asked: $userTopics")
            if (aiTopics.isNotBlank()) sb.appendLine("💬 Zarp answered: $aiTopics")
        }

        return sb.toString().trim()
    }

    // ═══════════════════════════════════════════
    // Public: Generate Response
    // ═══════════════════════════════════════════

    suspend fun generateResponse(
        prompt: String,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = "",
        useSearch: Boolean = false
    ): String {
        return if (useSearch) {
            generateWithSearch(prompt, modelName, chatHistory, customStyle)
        } else {
            generateBasic(prompt, modelName, chatHistory, customStyle)
        }
    }

    // ═══════════════════════════════════════════
    // Private: Basic Generation
    // ═══════════════════════════════════════════

    private suspend fun generateBasic(
        prompt: String,
        modelName: String,
        chatHistory: List<Message>,
        customStyle: String
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle)
            ?: return@withContext "⚠️ **API key not set.**\nGo to Settings → API Keys to add your Gemini key."

        try {
            val contextBlock = buildContext(chatHistory, prompt)
            Log.d(TAG, "📤 $modelName | ${chatHistory.size} msg history | ${prompt.take(60)}...")

            val response = model.generateContent(content {
                if (contextBlock.isNotBlank()) text(contextBlock)
                text(prompt)
            })

            val rawText = response.text
            if (rawText.isNullOrBlank()) {
                Log.w(TAG, "⚠️ Empty response from $modelName")
                return@withContext "⚠️ I couldn't generate a response. Please try again."
            }

            Log.d(TAG, "📥 Response: ${rawText.take(100)}...")
            rawText
        } catch (e: Exception) {
            Log.e(TAG, "❌ $modelName failed: ${e.localizedMessage}", e)
            handleApiError(e, modelName)
        }
    }

    // ═══════════════════════════════════════════
    // Private: Search-Powered Generation
    // ═══════════════════════════════════════════

    private suspend fun generateWithSearch(
        prompt: String,
        modelName: String,
        chatHistory: List<Message>,
        customStyle: String
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle)
            ?: return@withContext "⚠️ **API key not set.**"

        try {
            Log.d(TAG, "🔍 Search mode: fetching web data for: ${prompt.take(60)}...")

            val searchContext = WebSearchManager.searchForContext(
                query = prompt,
                context = context,
                fetchContent = false
            )

            val historyBlock = buildContext(chatHistory, prompt)
            val fullPrompt = buildString {
                if (searchContext.isNotBlank()) {
                    appendLine(searchContext)
                    appendLine()
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("📋 INSTRUCTIONS:")
                    appendLine("- Use the search results above to answer accurately")
                    appendLine("- Cite sources using [Source: title](url) format")
                    appendLine("- If results are insufficient, use your own knowledge")
                    appendLine("- Be concise but thorough")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine()
                }
                if (historyBlock.isNotBlank()) {
                    appendLine(historyBlock)
                    appendLine()
                }
                appendLine("User: $prompt")
            }

            Log.d(TAG, "📤 Search-enriched prompt (${fullPrompt.length} chars)")
            val response = model.generateContent(content { text(fullPrompt) })

            val rawText = response.text ?: "No response."
            Log.d(TAG, "📥 Search response: ${rawText.take(100)}...")
            rawText
        } catch (e: Exception) {
            Log.e(TAG, "❌ Search generation failed", e)
            handleApiError(e, modelName)
        }
    }

    // ═══════════════════════════════════════════
    // Image Generation
    // ═══════════════════════════════════════════

    suspend fun generateResponseWithImage(
        prompt: String,
        imageUri: Uri,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = ""
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle)
            ?: return@withContext "⚠️ **API key not set.**"

        try {
            Log.d(TAG, "📸 Processing image with $modelName")

            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open image stream")
                return@withContext "❌ Cannot read the image file."
            }

            val bitmap = inputStream.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                return@withContext "❌ Unsupported image format. Try JPEG or PNG."
            }

            val processedBitmap = if (bitmap.width > IMAGE_MAX_DIMENSION || bitmap.height > IMAGE_MAX_DIMENSION) {
                val ratio = minOf(IMAGE_MAX_DIMENSION.toFloat() / bitmap.width, IMAGE_MAX_DIMENSION.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap

            val shortHistory = chatHistory.takeLast(3)
            val older = chatHistory.dropLast(3)
            val summary = if (older.isNotEmpty()) {
                val u = older.filter { it.isUser }.joinToString("; ") { it.text.take(60) }
                val a = older.filter { !it.isUser }.joinToString("; ") { it.text.take(60) }
                "📋 Earlier: $u → 💬 $a"
            } else ""

            val response = model.generateContent(content {
                if (summary.isNotBlank()) text(summary)
                shortHistory.forEach { msg ->
                    if (msg.isUser) text("👤 ${msg.text}") else text("🤖 ${msg.text}")
                }
                image(processedBitmap)
                text(prompt.ifBlank { "Describe this image in detail. What do you see?" })
            })

            response.text ?: "⚠️ I couldn't analyze the image."
        } catch (e: Exception) {
            Log.e(TAG, "❌ Image processing failed", e)
            handleApiError(e, modelName)
        }
    }

    // ═══════════════════════════════════════════
    // Translation
    // ═══════════════════════════════════════════

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
            val response = model.generateContent(content {
                text("Translate this exactly to $targetLang. Return ONLY the translation.\n\n$text")
            })
            response.text ?: "Translation failed."
        } catch (e: Exception) {
            "⚠️ Translation unavailable."
        }
    }

    // ═══════════════════════════════════════════
    // Embedding Storage
    // ═══════════════════════════════════════════

    suspend fun storeMessageEmbedding(text: String) {
        if (text.isNotBlank()) {
            embeddingManager.embedAndStore(text)
        }
    }

    // ═══════════════════════════════════════════
    // Error Handler
    // ═══════════════════════════════════════════

    private fun handleApiError(e: Exception, modelName: String): String {
        val msg = e.localizedMessage ?: ""
        return when {
            msg.contains("403") -> "🔒 **Model unavailable**\n`$modelName` is not accessible on your plan.\n→ Switch to Gemini 2.5 Flash."
            msg.contains("404") -> "🔍 **Model not found**\n`$modelName` doesn't exist."
            msg.contains("429") -> "⏳ **Rate limit reached**\nPlease wait 30 seconds."
            msg.contains("503") || msg.contains("timeout", true) -> "⏰ **Service busy**\nTry again in a moment."
            msg.contains("MAX_TOKENS") -> "📏 **Response too long**\nTry a shorter question."
            msg.contains("SAFETY") || msg.contains("blocked") -> "🛡️ **Content blocked** by safety filters."
            else -> "❌ **Error:** ${e.localizedMessage ?: "Unknown"}"
        }
    }
}
