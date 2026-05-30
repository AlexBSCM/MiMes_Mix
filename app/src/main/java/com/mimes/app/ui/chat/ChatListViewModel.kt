package com.mimes.app.ui.chat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor() : ViewModel() {
    
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    init {
        loadDummyChats()
    }

    private fun loadDummyChats() {
        _chats.value = listOf(
            Chat(id = "1", name = "Общий чат", lastMessage = "Привет всем!", timestamp = "10:00", unreadCount = 2),
            Chat(id = "2", name = "Разработка", lastMessage = "Код готов к ревью", timestamp = "Вчера", unreadCount = 0),
            Chat(id = "3", name = "Дизайн", lastMessage = "Макеты утверждены", timestamp = "Вчера", unreadCount = 5)
        )
    }
}

data class Chat(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0
)
