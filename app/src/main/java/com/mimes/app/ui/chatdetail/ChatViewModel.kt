package com.mimes.app.ui.chatdetail

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mimes.app.ui.auth.Session
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.mimes.app.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    data class Error(val message: String) : UploadState()
}

@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId get() = Session.currentUserId

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private var listenerRegistration: ListenerRegistration? = null

    private val _uploadProgress = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadProgress: StateFlow<UploadState> = _uploadProgress

    // Загрузка файла в Storage и отправка сообщения
    fun uploadAndSendFile(chatId: String, peerId: String, text: String, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uploadProgress.value = UploadState.Uploading
            try {
                val fileName = getFileName(uri, contentResolver) ?: "file"
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val fileType = when {
                    mimeType.startsWith("image/") -> "image"
                    mimeType.startsWith("video/") -> "video"
                    mimeType.startsWith("audio/") -> "audio"
                    else -> "document"
                }
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Не удалось прочитать файл")
                val bytes = inputStream.use { it.readBytes() }

                val metadata = StorageMetadata.Builder()
                    .setContentType(mimeType)
                    .build()
                val storageRef = FirebaseStorage.getInstance("gs://mimes-f9a2d.firebasestorage.app").reference
                    .child("chat_files/${chatId.replace("@", "")}/${UUID.randomUUID()}/$fileName")

                val uploadTask = storageRef.putBytes(bytes, metadata).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await()

                sendMessage(
                    chatId = chatId,
                    peerId = peerId,
                    text = text,
                    hasFile = true,
                    fileName = fileName,
                    fileType = fileType,
                    fileUrl = downloadUrl.toString(),
                    fileSize = bytes.size.toLong()
                )
                _uploadProgress.value = UploadState.Idle
            } catch (e: Exception) {
                val code = if (e is com.google.firebase.storage.StorageException) " код=${e.errorCode}" else ""
                _uploadProgress.value = UploadState.Error("Ошибка: ${e.localizedMessage ?: e.message}$code:${e.javaClass.simpleName}")
            }
        }
    }

    private fun getFileName(uri: Uri, contentResolver: ContentResolver): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) it.getString(nameIndex) else null
        }
    }

    fun markChatAsRead(chatId: String) {
        db.collection("users").document(currentUserId)
            .update("lastReadTimestamps.$chatId", FieldValue.serverTimestamp())
    }

    // Загрузка сообщений из конкретного чата
    fun loadMessages(chatId: String) {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Message::class.java)
                    }.getOrNull()?.copy(id = doc.id)
                } ?: emptyList()
                _messages.value = list
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    // Отправка сообщения
    fun sendMessage(
        chatId: String,
        peerId: String,
        text: String,
        hasFile: Boolean = false,
        fileName: String = "",
        fileType: String = "",
        fileUrl: String = "",
        fileSize: Long = 0
    ) {
        if (text.isBlank() && !hasFile) return

        val message = Message(
            text = text,
            senderId = currentUserId,
            receiverId = peerId,
            timestamp = Date(),
            hasFile = hasFile,
            fileName = fileName,
            fileType = fileType,
            fileUrl = fileUrl,
            fileSize = fileSize
        )

        db.collection("chats").document(chatId).collection("messages")
            .add(message)
            .addOnFailureListener { e ->
                println("Ошибка отправки: ")
            }

        // Если пишем боту, запускаем имитацию ответа
        if (peerId == "bot") {
            simulateBotResponse(chatId, text)
        }
    }

    // Логика Бота
    private fun simulateBotResponse(chatId: String, userText: String) {
        val responses = listOf(
            "Интересно! Расскажите подробнее.",
            "Я просто тестовый бот, но я вас слышу!",
            "Сообщение получено. Обрабатываю...",
            "Привет! Как дела?",
            "Это автоматический ответ на: ''"
        )
        val randomResponse = responses.random()

        // Задержка 1-2 секунды
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            val botMessage = Message(
                text = randomResponse,
                senderId = "bot",
                receiverId = currentUserId,
                timestamp = Date()
            )
            db.collection("chats").document(chatId).collection("messages")
                .add(botMessage)
        }
    }
}
