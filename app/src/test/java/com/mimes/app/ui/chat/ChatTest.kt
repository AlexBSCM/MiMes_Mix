package com.mimes.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTest {

    @Test
    fun defaults() {
        val chat = Chat(id = "id", name = "@bob", lastMessage = "", timestamp = "")
        assertEquals("id", chat.id)
        assertEquals("@bob", chat.name)
        assertEquals("", chat.lastMessage)
        assertEquals("", chat.timestamp)
        assertEquals(0, chat.unreadCount)
    }

    @Test
    fun copy_updatesUnreadAndLastMessage() {
        val chat = Chat(id = "id", name = "@bob", lastMessage = "", timestamp = "")
        val updated = chat.copy(lastMessage = "Hello", unreadCount = 3)
        assertEquals("Hello", updated.lastMessage)
        assertEquals(3, updated.unreadCount)
        assertEquals(0, chat.unreadCount)
    }
}
