package com.example.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TTSManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    suspend fun speak(text: String, onComplete: () -> Unit) {
        val key = KeyManager.getGeminiKey(context) ?: return
        val model = GenerativeModel(
            modelName = "models/gemini-3.1-flash-tts-preview",  // Free TTS model
            apiKey = key
        )
        try {
            val response = model.generateContent(
                content {
                    text(text)
                }
            )
            // TTS returns audio bytes via the response
            val audioData = response.rawResponse?.body?.bytes()
            if (audioData != null) {
                val file = File(context.cacheDir, "zarp_speech.mp3")
                FileOutputStream(file).use { it.write(audioData) }
                withContext(Dispatchers.Main) {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            onComplete()
                            release()
                            mediaPlayer = null
                        }
                        start()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "TTS failed", e)
            onComplete()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
