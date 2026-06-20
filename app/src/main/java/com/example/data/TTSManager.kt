package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class TTSManager(private val context: Context) {

    companion object {
        private const val TAG = "TTSManager"
        private const val TTS_MODEL = "models/gemini-3.1-flash-tts-preview"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    /**
     * Speaks the given text using Gemini TTS.
     * Automatically cleans up resources when done.
     */
    suspend fun speak(text: String, onComplete: () -> Unit) {
        val key = KeyManager.getGeminiKey(context)
        if (key == null) {
            Log.e(TAG, "Gemini API key not set")
            onComplete()
            return
        }

        if (text.isBlank()) {
            Log.w(TAG, "Empty text, nothing to speak")
            onComplete()
            return
        }

        val model = GenerativeModel(
            modelName = TTS_MODEL,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.2f
                maxOutputTokens = 4096
            }
        )

        try {
            Log.d(TAG, "Requesting TTS for: ${text.take(50)}...")
            val response = model.generateContent(
                content {
                    text(text)
                }
            )

            val responseText = response.text
            if (responseText.isNullOrBlank()) {
                Log.e(TAG, "Empty response from TTS model")
                onComplete()
                return
            }

            val audioBytes = extractAudioFromResponse(responseText)
            if (audioBytes == null || audioBytes.isEmpty()) {
                Log.e(TAG, "No audio data in TTS response")
                onComplete()
                return
            }

            playAudio(audioBytes, onComplete)
        } catch (e: Exception) {
            Log.e(TAG, "TTS generation failed", e)
            onComplete()
        }
    }

    /**
     * Stops current playback immediately and cleans up.
     */
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media player", e)
        }
        mediaPlayer = null
        cleanupFile()
    }

    /**
     * Returns true if audio is currently playing.
     */
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    // ─── Private helpers ────────────────────

    /**
     * Extracts base64-encoded audio from the TTS JSON response.
     * Handles multiple possible JSON field names.
     */
    private fun extractAudioFromResponse(responseText: String): ByteArray? {
        return try {
            val json = JSONObject(responseText)

            // Try different possible field names for audio data
            val audioBase64 = json.optString("audio", "")
                .ifBlank { json.optString("audio_data", "") }
                .ifBlank { json.optString("audioContent", "") }
                .ifBlank { json.optString("data", "") }

            if (audioBase64.isBlank()) {
                // Check if there's a nested object
                val candidates = json.optJSONObject("candidates")
                val content = candidates?.optJSONArray("content")
                val firstPart = content?.optJSONObject(0)?.optJSONObject("inlineData")
                val nestedBase64 = firstPart?.optString("data", "")
                if (nestedBase64.isNotBlank()) {
                    return Base64.decode(nestedBase64, Base64.DEFAULT)
                }

                Log.e(TAG, "No audio field found in response")
                return null
            }

            Base64.decode(audioBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TTS JSON response", e)
            null
        }
    }

    /**
     * Plays audio bytes using MediaPlayer on the main thread.
     */
    private suspend fun playAudio(audioBytes: ByteArray, onComplete: () -> Unit) {
        withContext(Dispatchers.Main) {
            try {
                // Clean up previous player
                mediaPlayer?.release()
                mediaPlayer = null
                cleanupFile()

                // Write audio to cache file
                val file = File(context.cacheDir, "zarp_speech_${System.currentTimeMillis()}.mp3")
                FileOutputStream(file).use { it.write(audioBytes) }
                currentFile = file

                // Create and configure MediaPlayer
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build()
                    )
                    setDataSource(file.absolutePath)

                    setOnPreparedListener { start() }

                    setOnCompletionListener {
                        Log.d(TAG, "Playback completed")
                        onComplete()
                        release()
                        mediaPlayer = null
                        cleanupFile()
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        onComplete()
                        release()
                        mediaPlayer = null
                        cleanupFile()
                        true
                    }

                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start playback", e)
                onComplete()
                mediaPlayer?.release()
                mediaPlayer = null
                cleanupFile()
            }
        }
    }

    /**
     * Deletes the temporary audio file.
     */
    private fun cleanupFile() {
        try {
            currentFile?.delete()
            currentFile = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete temp file", e)
        }
    }
}
