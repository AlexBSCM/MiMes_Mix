package com.mimes.app.ui.profile

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.mimes.app.ui.auth.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

sealed class ProfileState {
    object Loading : ProfileState()
    data class Loaded(val nickname: String, val avatarUrl: String?) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

sealed class AvatarState {
    object Idle : AvatarState()
    object Uploading : AvatarState()
    data class Done(val url: String) : AvatarState()
    data class Error(val message: String) : AvatarState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val settingsPrefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val authPrefs = application.getSharedPreferences("auth", Context.MODE_PRIVATE)

    val currentUserId: String = Session.currentUserId

    private val _profile = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profile: StateFlow<ProfileState> = _profile

    private val _avatarState = MutableStateFlow<AvatarState>(AvatarState.Idle)
    val avatarState: StateFlow<AvatarState> = _avatarState

    private val _nicknameSaved = MutableStateFlow(false)
    val nicknameSaved: StateFlow<Boolean> = _nicknameSaved

    private val _notifySound = MutableStateFlow(settingsPrefs.getBoolean("notify_sound", true))
    val notifySound: StateFlow<Boolean> = _notifySound

    private val _notifyVibration = MutableStateFlow(settingsPrefs.getBoolean("notify_vibration", true))
    val notifyVibration: StateFlow<Boolean> = _notifyVibration

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (currentUserId.isBlank()) {
            _profile.value = ProfileState.Error("Не выполнен вход")
            return
        }
        _profile.value = ProfileState.Loading
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val nickname = doc.getString("nickname") ?: currentUserId.removePrefix("@")
                val avatarUrl = doc.getString("avatarUrl")
                _profile.value = ProfileState.Loaded(nickname, avatarUrl)
            }
            .addOnFailureListener { e ->
                _profile.value = ProfileState.Error(e.message ?: "Ошибка загрузки профиля")
            }
    }

    fun saveNickname(nickname: String) {
        val name = nickname.trim()
        if (name.isBlank()) return
        if (currentUserId.isBlank()) return
        _nicknameSaved.value = false
        db.collection("users").document(currentUserId)
            .update("nickname", name)
            .addOnSuccessListener { _nicknameSaved.value = true }
            .addOnFailureListener { }
    }

    fun uploadAvatar(uri: Uri, contentResolver: ContentResolver) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            _avatarState.value = AvatarState.Uploading
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Не удалось прочитать изображение")
                val bytes = inputStream.use { it.readBytes() }

                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build()
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("avatars/${currentUserId.removePrefix("@")}.jpg")

                val uploadTask = storageRef.putBytes(bytes, metadata).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await()

                db.collection("users").document(currentUserId)
                    .update("avatarUrl", downloadUrl.toString())

                _avatarState.value = AvatarState.Done(downloadUrl.toString())
                // Обновляем локальное состояние профиля
                (_profile.value as? ProfileState.Loaded)?.let {
                    _profile.value = it.copy(avatarUrl = downloadUrl.toString())
                }
            } catch (e: Exception) {
                _avatarState.value = AvatarState.Error(e.message ?: "Ошибка загрузки аватара")
            }
        }
    }

    fun setNotifySound(enabled: Boolean) {
        _notifySound.value = enabled
        settingsPrefs.edit().putBoolean("notify_sound", enabled).apply()
    }

    fun setNotifyVibration(enabled: Boolean) {
        _notifyVibration.value = enabled
        settingsPrefs.edit().putBoolean("notify_vibration", enabled).apply()
    }

    /** Выход из аккаунта: очищает сессию и настройки входа. */
    fun signOut() {
        Session.currentUserId = ""
        authPrefs.edit().clear().apply()
    }
}
