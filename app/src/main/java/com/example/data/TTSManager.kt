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
            modelName = "models/gemini-3.1-flash-tts-preview",
            apiKey = key
        )
        try {
            val response = model.generateContent(content { text(text) })
            val rawBytes = response.rawResponse?.body?.bytes()
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val file = File(context.cacheDir, "zarp_speech_${System.currentTimeMillis()}.mp3")
                FileOutputStream(file).use { it.write(rawBytes) }
                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            prepare()
                            setOnCompletionListener {
                                onComplete()
                                release()
                                mediaPlayer = null
                                file.delete()
                            }
                            setOnErrorListener { _, _, _ ->
                                onComplete()
                                release()
                                mediaPlayer = null
                                file.delete()
                                true
                            }
                            start()
                        }
                    } catch (e: Exception) {
                        Log.e("TTSManager", "Playback failed", e)
                        onComplete()
                    }
                }
            } else {
                Log.e("TTSManager", "No audio data received")
                onComplete()
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "TTS generation failed", e)
            onComplete()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
