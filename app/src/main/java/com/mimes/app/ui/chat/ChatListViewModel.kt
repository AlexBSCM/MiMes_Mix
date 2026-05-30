package com.mimes.app.ui.chat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ChatItem(
    val id: String,
    val peerName: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int
)

@HiltViewModel
class ChatListViewModel @Inject constructor() : ViewModel() {
    
    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats.asStateFlow()

    init {
        loadDummyChats()
    }

    private fun loadDummyChats() {
        _chats.value = listOf(
            ChatItem("1", "Алексей Иванов", "Привет, как дела?", "10:45", 2),
            ChatItem("2", "Мария Смирнова", "Файлы отправила на почту.", "Вчера", 0),
            ChatItem("3", "Иван Петров", "Ок, договорились.", "Пн", 1),
            ChatItem("4", "Анна Сидорова", "Где встретимся?", "Сб", 0),
            ChatItem("5", "Дмитрий Волков", "Отличная работа!", "15 мая", 0)
        )
    }
}
