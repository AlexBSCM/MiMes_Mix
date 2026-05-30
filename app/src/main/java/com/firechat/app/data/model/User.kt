package com.firechat.app.data.model

data class User(
    val id: String = "",
    val nickname: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)
