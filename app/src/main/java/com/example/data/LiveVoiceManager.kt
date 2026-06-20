package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class LiveVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "LiveVoiceManager"
        private const val LIVE_MODEL = "models/gemini-3.1-flash-live-preview"
        private const val SAMPLE_RATE = 24000
    }

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private var audioTrack: AudioTrack? = null

    suspend fun speakResponse(text: String, onComplete: () -> Unit) {
        val key = KeyManager.getGeminiKey(context) ?: run { onComplete(); return }

        val model = GenerativeModel(
            modelName = LIVE_MODEL,
            apiKey = key,
            generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                temperature = 0.7f
                maxOutputTokens = 2048
            }
        )

        try {
            _isSpeaking.value = true
            val response = model.generateContent(
                content {
                    text("Respond naturally to this in a spoken voice: $text")
                }
            )

            val audioBase64 = extractAudioFromResponse(response.text ?: "")
            if (!audioBase64.isNullOrBlank()) {
                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                playPcmAudio(audioBytes, onComplete)
            } else {
                // Fallback to TTS
                Log.w(TAG, "Live API returned no audio, falling back to TTS")
                TTSManager(context).speak(text, onComplete)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Live voice failed", e)
            _isSpeaking.value = false
            onComplete()
        }
    }

    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        _isSpeaking.value = false
    }

    private fun extractAudioFromResponse(responseText: String): String? {
        return try {
            val json = JSONObject(responseText)

            // Try multiple audio field locations
            var audio: String? = null

            if (json.has("audio")) audio = json.optString("audio", null)
            if (audio.isNullOrBlank() && json.has("audioContent")) audio = json.optString("audioContent", null)
            if (audio.isNullOrBlank() && json.has("data")) audio = json.optString("data", null)

            // Try nested inlineData
            if (audio.isNullOrBlank()) {
                val candidates = json.optJSONArray("candidates")
                val content = candidates?.optJSONObject(0)?.optJSONArray("content")
                val inlineData = content?.optJSONObject(0)?.optJSONObject("inlineData")
                audio = inlineData?.optString("data", null)
            }

            audio
        } catch (e: Exception) {
            null
        }
    }

    private fun playPcmAudio(audioBytes: ByteArray, onComplete: () -> Unit) {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioTrack?.play()
            audioTrack?.write(audioBytes, 0, audioBytes.size)
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback failed", e)
        } finally {
            _isSpeaking.value = false
            onComplete()
        }
    }
}
