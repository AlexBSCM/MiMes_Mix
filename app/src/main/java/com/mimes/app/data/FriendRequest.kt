package com.mimes.app.data

data class FriendRequest(
    val id: String = "",
    val from: String = "",
    val to: String = "",
    val status: String = "pending"
)
