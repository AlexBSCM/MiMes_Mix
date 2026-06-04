package com.mimes.app.ui.auth

object Session {
    var currentUserId: String = ""
    val isLoggedIn: Boolean get() = currentUserId.isNotBlank()
}