package com.mimes.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsTest {

    @Test
    fun chatIdFor_ordersLexicographically() {
        assertEquals("@alice_@bob", ChatUtils.chatIdFor("@alice", "@bob"))
    }

    @Test
    fun chatIdFor_sameIdRegardlessOfArgumentOrder() {
        assertEquals(
            ChatUtils.chatIdFor("@bob", "@alice"),
            ChatUtils.chatIdFor("@alice", "@bob")
        )
    }

    @Test
    fun chatIdFor_handlesUnderscoreInNames() {
        assertEquals("@a_@b", ChatUtils.chatIdFor("@a", "@b"))
    }

    @Test
    fun fileTypeFromMime_image() {
        assertEquals("image", ChatUtils.fileTypeFromMime("image/png"))
    }

    @Test
    fun fileTypeFromMime_video() {
        assertEquals("video", ChatUtils.fileTypeFromMime("video/mp4"))
    }

    @Test
    fun fileTypeFromMime_audio() {
        assertEquals("audio", ChatUtils.fileTypeFromMime("audio/mpeg"))
    }

    @Test
    fun fileTypeFromMime_document() {
        assertEquals("document", ChatUtils.fileTypeFromMime("application/pdf"))
    }

    @Test
    fun fileTypeFromMime_null_returnsDocument() {
        assertEquals("document", ChatUtils.fileTypeFromMime(null))
    }

    @Test
    fun fileTypeFromMime_empty_returnsDocument() {
        assertEquals("document", ChatUtils.fileTypeFromMime(""))
    }
}
