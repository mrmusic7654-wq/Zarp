package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.GeminiRepository
import com.example.data.MockData
import com.example.model.Conversation
import com.example.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {

    data class ChatUiState( /* ... unchanged ... */ )

    private val repository = GeminiRepository(BuildConfig.GEMINI_API_KEY)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // ... all other functions (onInputChanged, onMicTap, etc.) stay exactly the same ...

    fun onSend() {
        val currentText = _uiState.value.inputText
        if (currentText.isBlank()) return

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            text = currentText,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            isAiThinking = true,
            fileSelected = false
        )

        // Real Gemini call
        viewModelScope.launch {
            val responseText = repository.generateResponse(currentText)
            val aiMessage = Message(
                id = UUID.randomUUID().toString(),
                text = responseText,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMessage,
                isAiThinking = false
            )
        }
    }
}
