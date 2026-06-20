package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class AndroidTTSManager(context: Context) {

    companion object {
        private const val TAG = "AndroidTTS"
    }

    private var tts: TextToSpeech? = null
    private var onCompleteCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS engine initialized successfully")
                tts?.language = Locale.US
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "Speech started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "Speech completed: $utteranceId")
                        onCompleteCallback?.invoke()
                        onCompleteCallback = null
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Speech error: $utteranceId")
                        onCompleteCallback?.invoke()
                        onCompleteCallback = null
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        Log.d(TAG, "Speech stopped: $utteranceId, interrupted=$interrupted")
                        if (interrupted) {
                            onCompleteCallback?.invoke()
                            onCompleteCallback = null
                        }
                    }
                })
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String, onComplete: () -> Unit) {
        if (text.isBlank()) {
            onComplete()
            return
        }

        // Cancel any previous speech
        stop()

        onCompleteCallback = onComplete

        val utteranceId = UUID.randomUUID().toString()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.SUCCESS) {
            Log.d(TAG, "Speaking: ${text.take(50)}...")
        } else {
            Log.e(TAG, "speak() returned error code: $result")
            onComplete()
            onCompleteCallback = null
        }
    }

    fun stop() {
        tts?.stop()
        onCompleteCallback?.invoke()
        onCompleteCallback = null
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        onCompleteCallback = null
    }
}
