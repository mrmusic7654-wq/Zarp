package com.example.data.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WebSearchManager {

    private const val TAG = "WebSearchManager"
    
    // 🔁 Replace with your HF Space URL after deployment
    private const val HF_SPACE_URL = "https://yourname-zarp-search.hf.space/search"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

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

    /**
     * Execute a web search with optional full content fetching.
     */
    suspend fun search(
        query: String,
        fetchContent: Boolean = false
    ): SearchResponse = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$HF_SPACE_URL?q=$encoded&fetch=$fetchContent"

            Log.d(TAG, "🔍 Searching: $query (fetch=$fetchContent)")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ZarpAI/2.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (body.isNullOrBlank()) {
                Log.w(TAG, "Empty response from HF Space")
                return@withContext SearchResponse(query = query)
            }

            parseResponse(query, body)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            SearchResponse(query = query)
        }
    }

    /**
     * Parse JSON response from HF Space.
     */
    private fun parseResponse(query: String, json: String): SearchResponse {
        return try {
            val obj = JSONObject(json)

            // Parse results
            val resultsArr = obj.optJSONArray("results") ?: org.json.JSONArray()
            val results = (0 until resultsArr.length()).map { i ->
                val item = resultsArr.getJSONObject(i)
                SearchResult(
                    title = item.optString("title", "Untitled"),
                    url = item.optString("url", ""),
                    snippet = item.optString("snippet", ""),
                    engine = item.optString("engine", "Unknown"),
                    credibility = item.optInt("credibility", 5)
                )
            }

            // Parse video
            val videoObj = obj.optJSONObject("video")
            val video = if (videoObj != null) {
                YouTubeInfo(
                    id = videoObj.optString("id", ""),
                    title = videoObj.optString("title", ""),
                    author = videoObj.optString("author", ""),
                    thumbnail = videoObj.optString("thumbnail", ""),
                    description = videoObj.optString("description", "")
                )
            } else null

            // Parse content
            val contentArr = obj.optJSONArray("content") ?: org.json.JSONArray()
            val content = (0 until contentArr.length()).map { i ->
                val item = contentArr.getJSONObject(i)
                ContentResult(
                    url = item.optString("url", ""),
                    title = item.optString("title", ""),
                    text = item.optString("text", ""),
                    length = item.optInt("length", 0)
                )
            }

            // Parse stats
            val statsObj = obj.optJSONObject("stats")
            val stats = if (statsObj != null) {
                val enginesMap = mutableMapOf<String, Int>()
                val enginesObj = statsObj.optJSONObject("engines_searched")
                if (enginesObj != null) {
                    enginesObj.keys().forEach { key ->
                        enginesMap[key] = enginesObj.optInt(key, 0)
                    }
                }
                SearchStats(
                    totalFound = statsObj.optInt("total_found", 0),
                    afterDedup = statsObj.optInt("after_dedup", 0),
                    enginesSearched = enginesMap,
                    contentEnriched = statsObj.optInt("content_enriched", 0),
                    timeSeconds = statsObj.optDouble("time_seconds", 0.0)
                )
            } else SearchStats()

            // Build formatted context for Gemini
            val formattedContext = buildString {
                appendLine("🌐 Web Search Results for: \"$query\"")
                appendLine()

                if (results.isNotEmpty()) {
                    results.forEachIndexed { i, r ->
                        appendLine("[Source ${i + 1}: ${r.title}](${r.url})")
                        if (r.snippet.isNotBlank()) {
                            appendLine("   ${r.snippet.take(200)}")
                        }
                        appendLine()
                    }
                }

                if (video != null) {
                    appendLine("🎬 YouTube Video Found:")
                    appendLine("   Title: ${video.title}")
                    appendLine("   Channel: ${video.author}")
                    if (video.description.isNotBlank()) {
                        appendLine("   Description: ${video.description.take(500)}")
                    }
                    appendLine()
                }

                if (content.isNotEmpty()) {
                    appendLine("📄 Full Page Content:")
                    content.forEach { c ->
                        appendLine("── ${c.title.take(80)} ──")
                        appendLine(c.text.take(2000))
                        appendLine()
                    }
                }

                if (stats.enginesSearched.isNotEmpty()) {
                    appendLine("⚡ Search Stats:")
                    appendLine("   ${stats.enginesSearched.size} engines searched in ${stats.timeSeconds}s")
                    appendLine("   ${stats.totalFound} results → ${stats.afterDedup} after dedup")
                }
            }

            Log.d(TAG, "✅ Search complete: ${results.size} results, ${content.size} pages fetched")

            SearchResponse(
                query = query,
                results = results,
                video = video,
                content = content,
                stats = stats,
                formattedContext = formattedContext.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            SearchResponse(query = query)
        }
    }

    /**
     * Extract only the formatted context string (quick helper).
     */
    suspend fun searchForContext(query: String, fetchContent: Boolean = false): String {
        return search(query, fetchContent).formattedContext
    }
}
