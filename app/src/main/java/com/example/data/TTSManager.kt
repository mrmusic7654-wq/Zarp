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
            val response = model.generateContent(content { text(text) })

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

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    private fun extractAudioFromResponse(responseText: String): ByteArray? {
        return try {
            val json = JSONObject(responseText)

            var audioBase64: String? = null

            // Try top-level fields
            if (json.has("audio")) audioBase64 = json.optString("audio", null)
            if (audioBase64.isNullOrBlank() && json.has("audio_data")) audioBase64 = json.optString("audio_data", null)
            if (audioBase64.isNullOrBlank() && json.has("audioContent")) audioBase64 = json.optString("audioContent", null)
            if (audioBase64.isNullOrBlank() && json.has("data")) audioBase64 = json.optString("data", null)

            // Try nested candidates[0].content[0].inlineData.data
            if (audioBase64.isNullOrBlank()) {
                val candidates = json.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONArray("content")
                val firstPart = content?.optJSONObject(0)
                val inlineData = firstPart?.optJSONObject("inlineData")
                val nestedData = inlineData?.optString("data", null)
                if (!nestedData.isNullOrBlank()) {
                    audioBase64 = nestedData
                }
            }

            if (audioBase64.isNullOrBlank()) {
                Log.e(TAG, "No audio field found in TTS response")
                return null
            }

            Base64.decode(audioBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TTS JSON response", e)
            null
        }
    }

    private suspend fun playAudio(audioBytes: ByteArray, onComplete: () -> Unit) {
        withContext(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                mediaPlayer = null
                cleanupFile()

                val file = File(context.cacheDir, "zarp_speech_${System.currentTimeMillis()}.mp3")
                FileOutputStream(file).use { it.write(audioBytes) }
                currentFile = file

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

    private fun cleanupFile() {
        try {
            currentFile?.delete()
            currentFile = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete temp file", e)
        }
    }
}
