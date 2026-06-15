package com.mimes.app.ui.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.mimes.app.ui.auth.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://mimes-f9a2d.firebasestorage.app")

    private val _profile = MutableStateFlow(ProfileData())
    val profile: StateFlow<ProfileData> = _profile

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    private val _uploadProgress = MutableStateFlow(false)
    val uploadProgress: StateFlow<Boolean> = _uploadProgress

    fun loadProfile() {
        val userId = Session.currentUserId
        if (userId.isBlank()) return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(userId).get().await()
                val data = doc.data ?: return@launch
                _profile.value = ProfileData(
                    nickname = data["nickname"] as? String ?: userId.removePrefix("@"),
                    email = data["email"] as? String ?: "",
                    phone = data["phone"] as? String ?: "",
                    photoUrl = data["photoUrl"] as? String ?: "",
                    visibility = data["visibility"] as? Map<String, String>
                        ?: mapOf("nickname" to "public", "email" to "contacts", "phone" to "private")
                )
            } catch (_: Exception) {}
        }
    }

    fun updateNickname(value: String) { _profile.value = _profile.value.copy(nickname = value) }
    fun updateEmail(value: String) { _profile.value = _profile.value.copy(email = value) }
    fun updatePhone(value: String) { _profile.value = _profile.value.copy(phone = value) }
    fun updateVisibility(field: String, level: String) {
        _profile.value = _profile.value.copy(
            visibility = _profile.value.visibility + (field to level)
        )
    }

    fun uploadPhoto(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uploadProgress.value = true
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot read file")
                val bytes = inputStream.use { it.readBytes() }
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
                val ref = storage.reference.child("profile_photos/${Session.currentUserId}/${UUID.randomUUID()}")
                val uploadTask = ref.putBytes(bytes, metadata).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await()
                _profile.value = _profile.value.copy(photoUrl = downloadUrl.toString())
            } catch (_: Exception) {}
            _uploadProgress.value = false
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val p = _profile.value
                val updates = mapOf(
                    "nickname" to p.nickname,
                    "email" to p.email,
                    "phone" to p.phone,
                    "photoUrl" to p.photoUrl,
                    "visibility" to p.visibility
                )
                db.collection("users").document(Session.currentUserId).update(updates).await()
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Ошибка сохранения")
            }
        }
    }

    fun resetSaveState() { _saveState.value = SaveState.Idle }

    data class ProfileData(
        val nickname: String = "",
        val email: String = "",
        val phone: String = "",
        val photoUrl: String = "",
        val visibility: Map<String, String> = emptyMap()
    )

    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        object Success : SaveState()
        data class Error(val message: String) : SaveState()
    }
}
