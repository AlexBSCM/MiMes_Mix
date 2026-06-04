package com.mimes.app.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val receiverId: String = "", // Для бота это будет "bot"
    @ServerTimestamp
    val timestamp: Date? = null,
    val isRead: Boolean = false,
    val hasFile: Boolean = false,
    val fileName: String = "",
    val fileType: String = "", // image / video / audio / document
    val fileUrl: String = "",
    val fileSize: Long = 0
)
