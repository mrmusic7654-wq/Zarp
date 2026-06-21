package com.example.data
import com.example.data.search.WebSearchManager
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
import java.io.ByteArrayOutputStream

class GeminiRepository(private val context: Context) {

    companion object {
        private const val TAG = "GeminiRepo"
        private const val MAX_OUTPUT_TOKENS = 8192
        private const val IMAGE_QUALITY = 80
        private const val MAX_FILE_CHARS = 4000
    }

    private val fullContextCount = 5
    private val embeddingManager = EmbeddingManager(context)

    // ──────────────────────────────────────────────
    // Model Factory
    // ──────────────────────────────────────────────
    private fun getModel(
        modelName: String,
        customStyle: String = ""
    ): GenerativeModel? {
        val key = KeyManager.getGeminiKey(context)
        if (key.isNullOrBlank()) {
            Log.e(TAG, "❌ Gemini API key is null or empty")
            return null
        }

        val systemPrompt = buildSystemPrompt(customStyle)

        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = MAX_OUTPUT_TOKENS
            },
            systemInstruction = content { text(systemPrompt) }
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
- Use markdown tables when comparing things:
  | Option | Pros | Cons |
  |--------|------|------|
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
- Keep responses comprehensive but not bloated.
            """.trimIndent()
        }
    }
    /**
 * Generate response with web search context injected.
 * Uses HF Space for search, Gemini for intelligent summarization.
 */
suspend fun generateResponseWithSearch(
    prompt: String,
    modelName: String,
    chatHistory: List<Message> = emptyList(),
    customStyle: String = "",
    fetchContent: Boolean = false
): String = withContext(Dispatchers.IO) {
    val model = getModel(modelName, customStyle)
        ?: return@withContext "⚠️ API key not set."

    try {
        Log.d(TAG, "🔍 Search mode: fetching web data for: ${prompt.take(60)}...")

        // Step 1: Get search results from HF Space
        val searchResponse = WebSearchManager.searchForContext(prompt, fetchContent)

        // Step 2: Build full prompt with search context
        val contextBlock = buildContext(chatHistory, prompt)
        val fullPrompt = buildString {
            if (searchResponse.isNotBlank()) {
                appendLine(searchResponse)
                appendLine()
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📋 INSTRUCTIONS:")
                appendLine("- Use the search results above to answer the user's question")
                appendLine("- Cite sources using [Source: title](url) format")
                appendLine("- If the search results are insufficient, use your own knowledge")
                appendLine("- Be accurate and concise")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                appendLine("User question: $prompt")
            } else {
                if (contextBlock.isNotBlank()) appendLine(contextBlock)
                appendLine(prompt)
            }
        }

        Log.d(TAG, "📤 Sending to Gemini (search-enriched prompt, ${fullPrompt.length} chars)")

        // Step 3: Send to Gemini
        val response = model.generateContent(content { text(fullPrompt) })
        val rawText = response.text ?: "No response."

        Log.d(TAG, "📥 Response received: ${rawText.take(100)}...")
        rawText
    } catch (e: Exception) {
        Log.e(TAG, "❌ Search-powered generation failed", e)
        handleApiError(e, modelName)
    }
}

/**
 * Hybrid generate — uses search if useSearch=true.
 */
suspend fun generateResponse(
    prompt: String,
    modelName: String,
    chatHistory: List<Message> = emptyList(),
    customStyle: String = "",
    useSearch: Boolean = false
): String {
    return if (useSearch) {
        generateResponseWithSearch(prompt, modelName, chatHistory, customStyle)
    } else {
        generateResponseBasic(prompt, modelName, chatHistory, customStyle)
    }
}

// Rename the existing generateResponse to this:
private suspend fun generateResponseBasic(
    prompt: String,
    modelName: String,
    chatHistory: List<Message> = emptyList(),
    customStyle: String = ""
): String = withContext(Dispatchers.IO) {
    val model = getModel(modelName, customStyle)
        ?: return@withContext "⚠️ API key not set."

    try {
        val contextBlock = buildContext(chatHistory, prompt)
        val response = model.generateContent(content {
            if (contextBlock.isNotBlank()) text(contextBlock)
            text(prompt)
        })
        response.text ?: "No response."
    } catch (e: Exception) {
        handleApiError(e, modelName)
    }
}

    // ──────────────────────────────────────────────
    // Context Builder — smart history compression
    // ──────────────────────────────────────────────
    private suspend fun buildContext(history: List<Message>, currentPrompt: String): String {
        if (history.isEmpty()) return ""

        val relevant = embeddingManager.searchSimilar(currentPrompt, topK = 3)
        val relevantSet = relevant.toSet()
        val sb = StringBuilder()

        // Last 5 messages — always full
        val recent = history.takeLast(fullContextCount)
        for (msg in recent) {
            val role = if (msg.isUser) "👤 User" else "🤖 Zarp"
            sb.appendLine("$role: ${msg.text}")
        }

        // Embedding-matched older messages
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

        // Compressed summary of everything else
        val rest = history.filter { it !in recent && it !in olderRelevant }
        if (rest.isNotEmpty()) {
            val userTopics = rest.filter { it.isUser }
                .map { it.text.take(60) }
                .distinct()
                .joinToString("; ")
            val aiTopics = rest.filter { !it.isUser }
                .map { it.text.take(60) }
                .distinct()
                .joinToString("; ")

            if (userTopics.isNotBlank()) sb.appendLine("📋 User asked: $userTopics")
            if (aiTopics.isNotBlank()) sb.appendLine("💬 Zarp answered: $aiTopics")
        }

        return sb.toString().trim()
    }

    // ──────────────────────────────────────────────
    // Generate Text Response
    // ──────────────────────────────────────────────
    suspend fun generateResponse(
        prompt: String,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = "",
        useSearch: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle)
        if (model == null) {
            return@withContext "⚠️ **API key not set.**\nGo to Settings → API Keys to add your Gemini key."
        }

        try {
            val finalPrompt = if (useSearch) {
                "🔍 *Web Search Mode active.* Use your most current knowledge. $prompt"
            } else {
                prompt
            }

            Log.d(TAG, "📤 Sending to $modelName | Search: $useSearch | History: ${chatHistory.size} msgs")
            val contextBlock = buildContext(chatHistory, finalPrompt)

            val response = model.generateContent(
                content {
                    if (contextBlock.isNotBlank()) text(contextBlock)
                    text(finalPrompt)
                }
            )

            val rawText = response.text
            if (rawText.isNullOrBlank()) {
                Log.w(TAG, "Empty response from $modelName")
                return@withContext "⚠️ I couldn't generate a response. Please try again."
            }

            Log.d(TAG, "📥 Response: ${rawText.take(100)}...")

            if (useSearch) {
                "$rawText\n\n---\n🌐 *Web knowledge accessed — verify critical info from official sources.*"
            } else {
                rawText
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ $modelName failed: ${e.localizedMessage}", e)
            handleApiError(e, modelName)
        }
    }

    // ──────────────────────────────────────────────
    // Generate Image Response
    // ──────────────────────────────────────────────
    suspend fun generateResponseWithImage(
        prompt: String,
        imageUri: Uri,
        modelName: String,
        chatHistory: List<Message> = emptyList(),
        customStyle: String = ""
    ): String = withContext(Dispatchers.IO) {
        val model = getModel(modelName, customStyle)
        if (model == null) {
            return@withContext "⚠️ **API key not set.**"
        }

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

            // Compress large images
            val processedBitmap = if (bitmap.width > 2048 || bitmap.height > 2048) {
                val ratio = minOf(2048f / bitmap.width, 2048f / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else bitmap

            val shortHistory = chatHistory.takeLast(3)
            val older = chatHistory.dropLast(3)
            val summary = if (older.isNotEmpty()) {
                val u = older.filter { it.isUser }.joinToString("; ") { it.text.take(60) }
                val a = older.filter { !it.isUser }.joinToString("; ") { it.text.take(60) }
                "📋 Earlier: $u → 💬 $a"
            } else ""

            val response = model.generateContent(
                content {
                    if (summary.isNotBlank()) text(summary)
                    shortHistory.forEach { msg ->
                        if (msg.isUser) text("👤 ${msg.text}")
                        else text("🤖 ${msg.text}")
                    }
                    image(processedBitmap)
                    text(prompt.ifBlank { "Describe this image in detail. What do you see? Provide useful observations." })
                }
            )

            val rawText = response.text
            if (rawText.isNullOrBlank()) {
                return@withContext "⚠️ I couldn't analyze the image. Try a clearer photo."
            }

            Log.d(TAG, "📥 Image analysis: ${rawText.take(100)}...")
            rawText
        } catch (e: Exception) {
            Log.e(TAG, "❌ Image processing failed: ${e.localizedMessage}", e)
            handleApiError(e, modelName)
        }
    }

    // ──────────────────────────────────────────────
    // Translation
    // ──────────────────────────────────────────────
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
                    text("Translate this exactly to $targetLang. Return ONLY the translation, no explanations.\n\n$text")
                }
            )
            response.text ?: "Translation failed."
        } catch (e: Exception) {
            Log.e(TAG, "❌ Translation failed", e)
            "⚠️ Translation unavailable right now."
        }
    }

    // ──────────────────────────────────────────────
    // Embedding Storage
    // ──────────────────────────────────────────────
    suspend fun storeMessageEmbedding(text: String) {
        if (text.isNotBlank()) {
            embeddingManager.embedAndStore(text)
        }
    }

    // ──────────────────────────────────────────────
    // Error Handler
    // ──────────────────────────────────────────────
    private fun handleApiError(e: Exception, modelName: String): String {
        val msg = e.localizedMessage ?: ""
        return when {
            msg.contains("403") ->
                "🔒 **Model unavailable**\n`$modelName` is not accessible on your plan.\n→ *Switch to Gemini 2.5 Flash in the model selector.*"
            msg.contains("404") ->
                "🔍 **Model not found**\n`$modelName` doesn't exist or was removed.\n→ *Choose a different model.*"
            msg.contains("429") ->
                "⏳ **Rate limit reached**\nToo many requests. Please wait 30 seconds."
            msg.contains("503") || msg.contains("timeout", true) ->
                "⏰ **Service busy**\nThe model is overloaded. Try again in a moment."
            msg.contains("MAX_TOKENS") ->
                "📏 **Response too long**\nTry a shorter question or split into parts."
            msg.contains("SAFETY") || msg.contains("blocked") ->
                "🛡️ **Content blocked**\nThe response was filtered by safety settings."
            else ->
                "❌ **Unexpected error**\n`${e.localizedMessage ?: "Unknown"}`\n→ *Try Gemini 2.5 Flash if this persists.*"
        }
    }
}
