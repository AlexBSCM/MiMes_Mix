package com.firechat.app.ui.profile

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class UserProfile(
    val id: String,
    val nickname: String,
    val status: String
)

@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        // Dummy data
        _profile.value = UserProfile(
            id = "current_user_123",
            nickname = "SuperUser2024",
            status = "В сети"
        )
    }

    fun logout() {
        // Logic to clear user session
        _profile.value = null
    }
}
