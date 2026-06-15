package com.mimes.app.ui.chat

data class Chat(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val missedCalls: Int = 0
)
