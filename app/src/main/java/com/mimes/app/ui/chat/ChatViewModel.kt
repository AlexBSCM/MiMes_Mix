package com.mimes.app.ui.chat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MessageItem(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val timestamp: String
)

@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    fun loadMessages(chatId: String) {
        // Load dummy messages based on chatId
        _messages.value = listOf(
            MessageItem("1", "Привет! Как продвигается проект?", false, "10:30"),
            MessageItem("2", "Привет. Все отлично, уже заканчиваю UI часть.", true, "10:35"),
            MessageItem("3", "Супер! Когда сможешь показать?", false, "10:40"),
            MessageItem("4", "Думаю, к вечеру будет готово.", true, "10:42"),
            MessageItem("5", "Отлично, жду.", false, "10:45")
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        
        val newMessage = MessageItem(
            id = System.currentTimeMillis().toString(),
            text = text.trim(),
            isMine = true,
            timestamp = currentTime
        )
        
        _messages.update { currentList ->
            currentList + newMessage
        }
    }
}
