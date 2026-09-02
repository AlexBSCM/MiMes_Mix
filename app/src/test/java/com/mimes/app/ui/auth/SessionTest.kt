package com.mimes.app.ui.auth

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    @After
    fun tearDown() {
        Session.currentUserId = ""
    }

    @Test
    fun isLoggedIn_falseWhenBlank() {
        Session.currentUserId = ""
        assertFalse(Session.isLoggedIn)
    }

    @Test
    fun isLoggedIn_trueWhenUserSet() {
        Session.currentUserId = "@test"
        assertTrue(Session.isLoggedIn)
    }

    @Test
    fun isLoggedIn_falseAfterClear() {
        Session.currentUserId = "@test"
        Session.currentUserId = ""
        assertFalse(Session.isLoggedIn)
    }
}
