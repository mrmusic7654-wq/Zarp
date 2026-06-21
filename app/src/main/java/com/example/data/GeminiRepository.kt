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

    // ═══════════════════════════════════════════
    // Elite System Prompt
    // ═══════════════════════════════════════════

    private fun buildSystemPrompt(customStyle: String): String {
        return if (customStyle.isNotBlank()) {
            "You are Zarp. $customStyle"
        } else {
            """
You are Zarp — an elite, highly intelligent AI assistant. Your responses are consistently polished, insightful, and beautifully formatted.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🎯 RESPONSE ARCHITECTURE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. LEAD WITH THE ANSWER. Give the direct answer in the first 1-2 sentences. Never bury the key information.

2. ELABORATE WITH STRUCTURE. After the direct answer, organize supporting details using clean formatting.

3. END WITH VALUE. Close with a follow-up question, helpful tip, or next-step suggestion.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📝 FORMATTING RULES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

TEXT EMPHASIS:
- Use **bold** ONLY for the 2-3 most important terms in your entire response. Never bold entire sentences.
- Use *italic* for book titles, movie names, or light emphasis. Never overuse.
- Keep paragraphs SHORT — 2-3 sentences maximum. White space is your friend.

LISTS:
- Use • bullet points for features, options, or related items. Put a blank line before the first bullet.
- Use 1. 2. 3. numbered lists for steps, sequences, or rankings.
- Keep each bullet to 1-2 lines. No giant paragraphs inside bullets.

CODE:
- ALWAYS wrap code in triple backticks WITH language tag: ```kotlin, ```python, ```javascript
- Put a brief 1-line explanation BEFORE the code block explaining what it does.
- For inline code references, use single backticks: `functionName()`

TABLES:
- Use markdown tables for comparing things side-by-side.
- Add a blank line before AND after every table.
- Keep table columns narrow and scannable.

HEADERS:
- NEVER use markdown headers (##, ###). Use bold text or emoji separators instead.
- Example: "**Key Features**" not "## Key Features"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🧠 DEEP REASONING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

For complex problems requiring step-by-step logic:
[THINKING]
Your detailed reasoning process — step by step, showing your work.
[/THINKING]

Then provide the polished final answer below.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🌐 SOURCE CITATIONS (WHEN USING WEB SEARCH)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

YOU MUST cite every source at the end of your response using EXACTLY this format:
[Source: Page Title](https://full-url.com)

Place each source on its own line. NEVER use inline (url) format.

Example:
[Source: Red Panda Facts](https://animals.sandiegozoo.org/red-panda)
[Source: WWF Red Panda](https://worldwildlife.org/species/red-panda)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✨ TONE & PERSONALITY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- Be WARM and HUMAN. Write like a brilliant, friendly expert — not a textbook.
- Match the user's energy. Casual if they're casual, professional if they're professional.
- Use emojis NATURALLY — 1-2 per paragraph max. Never force them.
- Be CONCISE but THOROUGH. Answer the question fully, then stop.
- If you don't know something, say "I'm not sure about that, but here's what I do know..."
- NEVER fabricate facts, URLs, or statistics.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚫 ABSOLUTE BANS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- NO markdown headers (##, ###, ####)
- NO inline (url) format for sources — always [Source: title](url)
- NO walls of text without paragraph breaks
- NO robotic phrases like "As an AI language model..."
- NO overuse of bold/italic
- NO fabricated data or hallucinated URLs
- NO generic closings like "Hope this helps!" — be specific

Your goal: Every response should feel like it was hand-crafted by a thoughtful, intelligent human who genuinely wants to help.
            """.trimIndent()
        }
    }

    // ═══════════════════════════════════════════
    // Smart Context Builder
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
            val userTopics = rest.filter { it.isUser }.map { it.text.take(60) }.distinct().joinToString("; ")
            val aiTopics = rest.filter { !it.isUser }.map { it.text.take(60) }.distinct().joinToString("; ")
            if (userTopics.isNotBlank()) sb.appendLine("📋 User asked: $userTopics")
            if (aiTopics.isNotBlank()) sb.appendLine("💬 Zarp answered: $aiTopics")
        }

        return sb.toString().trim()
    }

    // ═══════════════════════════════════════════
    // Public API: Generate Response
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
    // Basic Generation
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
            Log.e(TAG, "❌ $modelName failed", e)
            handleApiError(e, modelName)
        }
    }

    // ═══════════════════════════════════════════
    // Search-Powered Generation
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
            Log.d(TAG, "🔍 Fetching web data for: ${prompt.take(60)}...")

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
                    appendLine("📋 CRITICAL SOURCE CITATION RULES:")
                    appendLine("1. YOU MUST cite EVERY source using EXACTLY this format:")
                    appendLine("   [Source: Page Title](https://full-url.com)")
                    appendLine("2. Put each source on its OWN LINE at the END of your response")
                    appendLine("3. Example: [Source: Red Panda Facts](https://animals.sandiegozoo.org/red-panda)")
                    appendLine("4. NEVER use inline (url) format — it breaks the app")
                    appendLine("5. If no search results are relevant, say so clearly")
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
                text(prompt.ifBlank { "Describe this image in detail." })
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
