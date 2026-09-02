package com.mimes.app.ui.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@dagger.hilt.android.lifecycle.HiltViewModel
class AuthViewModel @javax.inject.Inject constructor(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs = application.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private fun persistSession(login: String) {
        Session.currentUserId = login
        prefs.edit().putString("login", login).apply()
    }

    /** Сохраняет FCM-токен устройства в профиле пользователя для отправки push. */
    private fun saveFcmToken(login: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                db.collection("users").document(login)
                    .update("fcmToken", token)
                    .addOnFailureListener { }
            }
        }
    }

    private suspend fun ensureFirebaseAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun signIn(login: String, password: String) {
        if (login.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Логин и пароль не могут быть пустыми")
            return
        }
        if (!login.startsWith("@")) {
            _authState.value = AuthState.Error("Логин должен начинаться с @")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                ensureFirebaseAuth()
                val doc = db.collection("users").document(login).get().await()
                if (doc.exists()) {
                    val storedPassword = doc.getString("password") ?: ""
                    if (storedPassword == password) {
                        persistSession(login)
                        saveFcmToken(login)
                        _authState.value = AuthState.Success(login)
                    } else {
                        _authState.value = AuthState.Error("Неверный пароль")
                    }
                } else {
                    _authState.value = AuthState.Error("Пользователь не найден")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Ошибка входа")
            }
        }
    }

    fun signUp(login: String, password: String) {
        if (login.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Логин и пароль не могут быть пустыми")
            return
        }
        if (!login.startsWith("@")) {
            _authState.value = AuthState.Error("Логин должен начинаться с @")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Пароль должен быть не менее 6 символов")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                ensureFirebaseAuth()
                val user = mapOf(
                    "password" to password,
                    "role" to "user",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(login).set(user).await()
                persistSession(login)
                saveFcmToken(login)
                _authState.value = AuthState.Success(login)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Ошибка регистрации")
            }
        }
    }

    fun signOut() {
        Session.currentUserId = ""
        prefs.edit().clear().apply()
        _authState.value = AuthState.Idle
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val userId: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}
