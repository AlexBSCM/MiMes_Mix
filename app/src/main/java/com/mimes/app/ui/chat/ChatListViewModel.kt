package com.mimes.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mimes.app.ui.auth.Session
import com.mimes.app.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class ChatListViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _searchResults = MutableStateFlow<List<String>>(emptyList())
    val searchResults: StateFlow<List<String>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    init {
        loadChats()
    }

    fun loadChats() {
        val userId = Session.currentUserId
        if (userId.isBlank()) return

        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                val contacts = snapshot?.get("contacts") as? List<String> ?: emptyList()
                val lastReadMap = snapshot?.get("lastReadTimestamps") as? Map<String, Any> ?: emptyMap()
                val chats = contacts.map { contact ->
                    val chatId = listOf(userId, contact).sorted().joinToString("_")
                    val lastRead = lastReadMap[chatId]
                    Chat(id = chatId, name = contact, lastMessage = "", timestamp = "", unreadCount = 0)
                }
                val base = listOf(
                    Chat(id = "bot_chat_001", name = "Бот", lastMessage = "Напишите мне!", timestamp = "", unreadCount = 0),
                    Chat(id = "general_chat", name = "Общий чат", lastMessage = "Добро пожаловать в MiMes", timestamp = "", unreadCount = 0)
                )
                _chats.value = base + chats
                base.forEach { fetchChatInfo(it, null) }
                chats.forEachIndexed { i, chat ->
                    val ls = lastReadMap[chat.id]
                    fetchChatInfo(chat, ls as? com.google.firebase.Timestamp)
                }
            }
    }

    private fun fetchChatInfo(chat: Chat, lastRead: Timestamp?) {
        val ref = db.collection("chats").document(chat.id).collection("messages")
        ref.orderBy("timestamp", Query.Direction.DESCENDING).limit(1).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                val msg = snapshot.documents[0].toObject(Message::class.java) ?: return@addOnSuccessListener
                val lastMsg = if (msg.text.isNotBlank()) msg.text.take(60) else if (msg.hasFile) "Файл" else ""
                val ts = msg.timestamp?.let { fmtTime(it) } ?: ""

                if (lastRead != null && msg.timestamp?.after(lastRead.toDate()) == true) {
                    ref.whereGreaterThan("timestamp", lastRead.toDate()).get()
                        .addOnSuccessListener { snap ->
                            updateChat(chat.id, lastMsg, ts, snap.size())
                        }
                } else {
                    updateChat(chat.id, lastMsg, ts, 0)
                }
            }
    }

    private fun updateChat(id: String, lastMsg: String, ts: String, unread: Int) {
        _chats.value = _chats.value.map {
            if (it.id == id) it.copy(lastMessage = lastMsg, timestamp = ts, unreadCount = unread) else it
        }
    }

    private fun fmtTime(date: Date): String {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { time = date }
        return if (now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == cal.get(Calendar.YEAR))
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
    }

    fun toggleSearch() {
        _isSearching.value = !_isSearching.value
        if (!_isSearching.value) {
            _searchResults.value = emptyList()
        }
    }

    fun searchUsers(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.documents
                    .mapNotNull { it.id }
                    .filter {
                        it != Session.currentUserId &&
                        it.contains(query, ignoreCase = true)
                    }
                _searchResults.value = results
            }
            .addOnFailureListener {
                _searchResults.value = emptyList()
            }
    }

    fun addContact(contactUserId: String) {
        val userId = Session.currentUserId
        if (userId.isBlank()) return

        db.collection("users").document(userId)
            .update("contacts", FieldValue.arrayUnion(contactUserId))
        _searchResults.value = _searchResults.value.filter { it != contactUserId }
    }
}
