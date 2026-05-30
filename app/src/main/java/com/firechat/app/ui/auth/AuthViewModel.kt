package com.firechat.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firechat.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val uid: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signIn(nickname: String) {
        if (nickname.isBlank()) {
            _authState.value = AuthState.Error("Никнейм не может быть пустым")
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            authRepository.signInAnonymously(nickname).collect { result ->
                result.fold(
                    onSuccess = { uid ->
                        _authState.value = AuthState.Success(uid)
                    },
                    onFailure = { error ->
                        _authState.value = AuthState.Error(error.message ?: "Ошибка авторизации")
                    }
                )
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
