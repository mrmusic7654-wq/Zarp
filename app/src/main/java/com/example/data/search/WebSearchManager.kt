package com.example.data.search

import android.content.Context
import android.util.Log
import com.example.data.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WebSearchManager {

    private const val TAG = "WebSearch"
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 1500L
    private const val CACHE_TTL_MS = 300_000L // 5 minutes

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Simple in-memory cache
    private val cache = mutableMapOf<String, CacheEntry>()
    private data class CacheEntry(val response: SearchResponse, val timestamp: Long)

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String,
        val engine: String,
        val credibility: Int = 5
    )

    data class YouTubeInfo(
        val id: String,
        val title: String,
        val author: String,
        val thumbnail: String,
        val description: String
    )

    data class ContentResult(
        val url: String,
        val title: String,
        val text: String,
        val length: Int = 0
    )

    data class SearchStats(
        val totalFound: Int = 0,
        val afterDedup: Int = 0,
        val enginesSearched: Map<String, Int> = emptyMap(),
        val contentEnriched: Int = 0,
        val timeSeconds: Double = 0.0
    )

    data class SearchResponse(
        val query: String = "",
        val results: List<SearchResult> = emptyList(),
        val video: YouTubeInfo? = null,
        val content: List<ContentResult> = emptyList(),
        val stats: SearchStats = SearchStats(),
        val formattedContext: String = ""
    )

    // ═══════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════

    suspend fun search(
        query: String,
        context: Context,
        fetchContent: Boolean = false
    ): SearchResponse = withContext(Dispatchers.IO) {
        val cacheKey = "$query:$fetchContent"
        
        // Check cache
        cache[cacheKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                Log.d(TAG, "📦 Cache hit: ${query.take(50)}")
                return@withContext entry.response
            }
        }

        val spaceUrl = KeyManager.getHFSpaceUrl(context)
        if (spaceUrl.isBlank()) {
            Log.w(TAG, "⚠️ HF Space URL not configured")
            return@withContext SearchResponse(query = query)
        }

        // Retry logic
        var lastError: String? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val fullUrl = "$spaceUrl?q=$encoded&fetch=$fetchContent"
                
                Log.d(TAG, "🔍 Searching (attempt ${attempt + 1}): ${query.take(60)}")
                
                val request = Request.Builder().url(fullUrl).header("User-Agent", "ZarpAI/3.0").get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (body.isNullOrBlank()) {
                    lastError = "Empty response"
                    if (attempt < MAX_RETRIES - 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
                    return@repeat
                }

                val result = parseResponse(query, body)
                if (result.results.isEmpty() && attempt < MAX_RETRIES - 1) {
                    lastError = "No results"
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                    return@repeat
                }

                // Cache successful result
                cache[cacheKey] = CacheEntry(result, System.currentTimeMillis())
                Log.d(TAG, "✅ ${result.results.size} results from ${result.stats.enginesSearched.size} engines")
                return@withContext result
            } catch (e: Exception) {
                lastError = e.localizedMessage
                Log.w(TAG, "⚠️ Attempt ${attempt + 1} failed: ${e.localizedMessage}")
                if (attempt < MAX_RETRIES - 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
        }

        Log.e(TAG, "❌ All retries failed: $lastError")
        SearchResponse(query = query)
    }

    /**
     * Shortcut: returns only formatted context for Gemini injection.
     */
    suspend fun searchForContext(
        query: String,
        context: Context,
        fetchContent: Boolean = false
    ): String {
        return search(query, context, fetchContent).formattedContext
    }

    // ═══════════════════════════════════════════
    // Response Parser
    // ═══════════════════════════════════════════

    private fun parseResponse(query: String, json: String): SearchResponse {
        return try {
            val obj = JSONObject(json)

            // Parse results
            val resultsArr = obj.optJSONArray("results") ?: org.json.JSONArray()
            val results = (0 until resultsArr.length()).map { i ->
                val item = resultsArr.getJSONObject(i)
                SearchResult(
                    title = item.optString("title", "Untitled").trim().ifBlank { "Untitled" },
                    url = item.optString("url", "").trim(),
                    snippet = item.optString("snippet", "").trim().take(500),
                    engine = item.optString("engine", "Web"),
                    credibility = item.optInt("credibility", 5)
                )
            }.filter { it.url.isNotBlank() }

            // Parse video
            val videoObj = obj.optJSONObject("video")
            val video = if (videoObj != null) {
                YouTubeInfo(
                    id = videoObj.optString("id", ""),
                    title = videoObj.optString("title", ""),
                    author = videoObj.optString("author", ""),
                    thumbnail = videoObj.optString("thumbnail", ""),
                    description = videoObj.optString("description", "").take(3000)
                )
            } else null

            // Parse content
            val contentArr = obj.optJSONArray("content") ?: org.json.JSONArray()
            val content = (0 until contentArr.length()).map { i ->
                val item = contentArr.getJSONObject(i)
                ContentResult(
                    url = item.optString("url", ""),
                    title = item.optString("title", ""),
                    text = item.optString("text", "").take(4000),
                    length = item.optInt("length", 0)
                )
            }

            // Parse stats
            val statsObj = obj.optJSONObject("stats")
            val stats = if (statsObj != null) {
                val enginesMap = mutableMapOf<String, Int>()
                val enginesObj = statsObj.optJSONObject("engines_searched")
                if (enginesObj != null) {
                    enginesObj.keys().forEach { key -> enginesMap[key] = enginesObj.optInt(key, 0) }
                }
                SearchStats(
                    totalFound = statsObj.optInt("total_found", 0),
                    afterDedup = statsObj.optInt("after_dedup", 0),
                    enginesSearched = enginesMap,
                    contentEnriched = statsObj.optInt("content_enriched", 0),
                    timeSeconds = statsObj.optDouble("time_seconds", 0.0)
                )
            } else SearchStats()

            // Build formatted context
            val formattedContext = buildContextString(query, results, video, content, stats)

            SearchResponse(query, results, video, content, stats, formattedContext)
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.localizedMessage}")
            SearchResponse(query = query)
        }
    }

    private fun buildContextString(
        query: String,
        results: List<SearchResult>,
        video: YouTubeInfo?,
        content: List<ContentResult>,
        stats: SearchStats
    ): String = buildString {
        appendLine("🌐 Web Search: \"$query\"")
        appendLine()

        if (results.isNotEmpty()) {
            results.forEachIndexed { i, r ->
                appendLine("[Source ${i + 1}: ${r.title}](${r.url})")
                if (r.snippet.isNotBlank()) appendLine("   ${r.snippet.take(250)}")
                appendLine()
            }
        }

        if (video != null && video.title.isNotBlank()) {
            appendLine("🎬 YouTube: ${video.title} by ${video.author}")
            if (video.description.isNotBlank()) appendLine("   ${video.description.take(500)}")
            appendLine()
        }

        if (content.isNotEmpty()) {
            appendLine("📄 Full Content:")
            content.forEach { c ->
                if (c.text.isNotBlank()) {
                    appendLine("── ${c.title.take(80)} ──")
                    appendLine(c.text.take(2000))
                    appendLine()
                }
            }
        }

        if (stats.enginesSearched.isNotEmpty()) {
            appendLine("⚡ ${stats.enginesSearched.size} engines | ${stats.totalFound} results | ${stats.timeSeconds}s")
        }
    }

    // ═══════════════════════════════════════════
    // Cache Management
    // ═══════════════════════════════════════════

    fun clearCache() {
        cache.clear()
        Log.d(TAG, "🗑️ Cache cleared")
    }

    fun getCacheSize(): Int = cache.size
}
