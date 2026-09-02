package com.mimes.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    @Test
    fun defaults_areSane() {
        val message = Message()
        assertTrue("id must not be blank", message.id.isNotBlank())
        assertEquals("", message.text)
        assertEquals("", message.senderId)
        assertEquals("", message.receiverId)
        assertNull(message.timestamp)
        assertFalse(message.isRead)
        assertFalse(message.hasFile)
        assertEquals(0L, message.fileSize)
    }

    @Test
    fun copy_updatesFields() {
        val message = Message(text = "Hi", hasFile = true, fileSize = 2048)
        val updated = message.copy(isRead = true, text = "Hello")
        assertTrue(updated.isRead)
        assertEquals("Hello", updated.text)
        assertEquals(2048L, updated.fileSize)
        assertTrue(updated.hasFile)
        assertEquals("Hi", message.text)
    }
}
