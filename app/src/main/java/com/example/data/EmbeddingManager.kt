package com.example.data

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EmbeddingManager(private val context: Context) {

    private data class EmbeddedMessage(
        val text: String,
        val embedding: FloatArray
    )

    private val storedEmbeddings = mutableListOf<EmbeddedMessage>()

    suspend fun embedAndStore(text: String) {
        val key = KeyManager.getGeminiKey(context) ?: return
        val model = GenerativeModel(
            modelName = "models/gemini-embedding-2",  // Free embedding model
            apiKey = key
        )
        try {
            val response = model.generateContent(content { text(text) })
            val jsonStr = response.text ?: return
            val json = JSONObject(jsonStr)
            val values = json.getJSONArray("values").getJSONArray(0)
            val embedding = FloatArray(values.length()) { values.getDouble(it).toFloat() }
            storedEmbeddings.add(EmbeddedMessage(text, embedding))
            Log.d("Embedding", "Stored embedding for: ${text.take(40)}...")
        } catch (e: Exception) {
            Log.e("Embedding", "Failed to embed", e)
        }
    }

    suspend fun searchSimilar(query: String, topK: Int = 5): List<String> {
        if (storedEmbeddings.isEmpty()) return emptyList()
        val key = KeyManager.getGeminiKey(context) ?: return emptyList()
        val model = GenerativeModel(
            modelName = "models/gemini-embedding-2",
            apiKey = key
        )
        try {
            val response = model.generateContent(content { text(query) })
            val jsonStr = response.text ?: return emptyList()
            val json = JSONObject(jsonStr)
            val values = json.getJSONArray("values").getJSONArray(0)
            val queryEmbedding = FloatArray(values.length()) { values.getDouble(it).toFloat() }

            return storedEmbeddings
                .map { it to cosineSimilarity(queryEmbedding, it.embedding) }
                .sortedByDescending { it.second }
                .take(topK)
                .map { it.first.text }
        } catch (e: Exception) {
            Log.e("Embedding", "Search failed", e)
            return emptyList()
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB) + 1e-10f)
    }
}
