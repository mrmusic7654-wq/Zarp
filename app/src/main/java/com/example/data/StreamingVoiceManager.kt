package com.example.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StreamingVoiceManager(context: Context) {

    companion object {
        private const val TAG = "StreamingVoice"
        private const val SILENCE_TIMEOUT_MS = 2000L
    }

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class VoiceState(
        val isListening: Boolean = false,
        val partialText: String = "",
        val finalText: String = "",
        val isProcessing: Boolean = false,
        val error: String? = null,
        val rmsLevel: Float = 0f,
        val silenceDuration: Long = 0L
    )

    data class VoiceCommand(
        val type: CommandType,
        val originalText: String,
        val correctedText: String
    )

    enum class CommandType {
        DELETE_LAST, CORRECTION, UNDO, SCRATCH, NONE
    }

    // ═══════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════

    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val commandChannel = Channel<VoiceCommand>(Channel.CONFLATED)
    private var lastSilenceTime = System.currentTimeMillis()
    private var accumulatedText = StringBuilder()

    // Correction phrases
    private val correctionPhrases = listOf(
        "delete that", "no delete that", "scratch that", "undo",
        "no i meant", "no i said", "no wait", "actually",
        "change that to", "replace that with", "i mean"
    )

    // ═══════════════════════════════════════════
    // Recognition Listener
    // ═══════════════════════════════════════════

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "🎤 Ready for speech")
            _voiceState.value = _voiceState.value.copy(
                isListening = true, error = null, partialText = "", finalText = ""
            )
            accumulatedText.clear()
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "🗣️ Speech started")
            _voiceState.value = _voiceState.value.copy(isListening = true)
        }

        override fun onRmsChanged(rmsdB: Float) {
            _voiceState.value = _voiceState.value.copy(rmsLevel = rmsdB)
            if (rmsdB < 1.0f) {
                lastSilenceTime = System.currentTimeMillis()
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "🔇 Speech ended")
            _voiceState.value = _voiceState.value.copy(isListening = false, isProcessing = true)
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error ($error)"
            }
            Log.e(TAG, "❌ Voice error: $errorMsg")
            _voiceState.value = _voiceState.value.copy(
                isListening = false, isProcessing = false, error = errorMsg
            )
        }

        override fun onResults(results: Bundle?) {
            _voiceState.value = _voiceState.value.copy(isProcessing = false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val rawText = matches?.firstOrNull() ?: ""
            Log.d(TAG, "📝 Final: $rawText")

            val corrected = applyCorrection(rawText)
            _voiceState.value = _voiceState.value.copy(
                isListening = false, finalText = corrected, partialText = ""
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            _voiceState.value = _voiceState.value.copy(partialText = partial)
            Log.d(TAG, "📝 Partial: $partial")
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ═══════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════

    fun startListening() {
        try {
            _voiceState.value = VoiceState()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
            }
            speechRecognizer.setRecognitionListener(recognitionListener)
            speechRecognizer.startListening(intent)
            Log.d(TAG, "🎤 Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start listening", e)
            _voiceState.value = _voiceState.value.copy(error = "Failed to start: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer.stopListening()
            _voiceState.value = _voiceState.value.copy(isListening = false, isProcessing = false)
            Log.d(TAG, "🛑 Stopped listening")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop", e)
        }
    }

    fun cancel() {
        try {
            speechRecognizer.cancel()
            _voiceState.value = VoiceState()
            accumulatedText.clear()
            Log.d(TAG, "❌ Cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel", e)
        }
    }

    fun destroy() {
        try {
            speechRecognizer.destroy()
            Log.d(TAG, "💀 Destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to destroy", e)
        }
    }

    fun getFinalTextAndReset(): String {
        val text = _voiceState.value.finalText
        _voiceState.value = VoiceState()
        accumulatedText.clear()
        return text
    }

    // ═══════════════════════════════════════════
    // Voice Commands
    // ═══════════════════════════════════════════

    private fun applyCorrection(text: String): String {
        var corrected = text.trim()

        for (phrase in correctionPhrases) {
            if (corrected.lowercase().contains(phrase)) {
                val index = corrected.lowercase().lastIndexOf(phrase)
                if (index >= 0) {
                    corrected = corrected.substring(0, index).trim()
                    if (corrected.isEmpty() && accumulatedText.isNotEmpty()) {
                        corrected = accumulatedText.toString().trim()
                    }
                    Log.d(TAG, "🔧 Correction detected: '$phrase' → kept: '$corrected'")
                }
            }
        }

        if (corrected.isNotEmpty() && corrected[0].isLowerCase()) {
            corrected = corrected[0].uppercaseChar() + corrected.substring(1)
        }

        accumulatedText.append("$corrected ")
        return corrected
    }

    // ═══════════════════════════════════════════
    // Punctuation (post-processing via Gemini)
    // ═══════════════════════════════════════════

    suspend fun addPunctuation(text: String, geminiRepository: GeminiRepository): String {
        if (text.isBlank()) return text
        return try {
            val response = geminiRepository.generateResponse(
                prompt = "Add proper punctuation, capitalization, and formatting to this dictation. Return ONLY the corrected text.\n\n$text",
                modelName = "models/gemini-2.5-flash"
            )
            response.ifBlank { text }
        } catch (e: Exception) {
            text
        }
    }
}
