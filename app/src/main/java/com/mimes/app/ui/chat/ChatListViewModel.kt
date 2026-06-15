package com.mimes.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mimes.app.ui.auth.Session
import com.mimes.app.data.Message
import com.mimes.app.data.FriendRequest
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
    private var contactsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var requestsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _searchResults = MutableStateFlow<List<String>>(emptyList())
    val searchResults: StateFlow<List<String>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = _incomingRequests

    init {
        loadChats()
        listenForRequests()
    }

    fun loadChats() {
        val userId = Session.currentUserId
        if (userId.isBlank()) return

        contactsListener?.remove()
        contactsListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                val contacts = snapshot?.get("contacts") as? List<String> ?: emptyList()
                val lastReadMap = snapshot?.get("lastReadTimestamps") as? Map<String, Any> ?: emptyMap()
                val missedCallsMap = snapshot?.get("missedCalls") as? Map<String, Any> ?: emptyMap()
                val chats = contacts.map { contact ->
                    val chatId = listOf(userId, contact).sorted().joinToString("_")
                    val lastRead = lastReadMap[chatId]
                    val missed = (missedCallsMap[contact] as? Number)?.toInt() ?: 0
                    Chat(id = chatId, name = contact, lastMessage = "", timestamp = "", unreadCount = 0, missedCalls = missed)
                }
                _chats.value = chats
                chats.forEach { chat ->
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
                    ref.whereGreaterThan("timestamp", lastRead.toDate())
                        .whereNotEqualTo("senderId", Session.currentUserId)
                        .get()
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

    override fun onCleared() {
        super.onCleared()
        contactsListener?.remove()
        contactsListener = null
        requestsListener?.remove()
        requestsListener = null
    }

    fun clearMissedCalls(peerName: String) {
        val userId = Session.currentUserId
        if (userId.isBlank()) return
        db.collection("users").document(userId)
            .update("missedCalls.$peerName", FieldValue.delete())
        _chats.value = _chats.value.map {
            if (it.name == peerName) it.copy(missedCalls = 0) else it
        }
    }

    companion object {
        fun clearMissedCallsStatic(peerName: String) {
            val userId = Session.currentUserId
            if (userId.isBlank()) return
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("missedCalls.$peerName", FieldValue.delete())
        }
    }

    fun addContact(contactUserId: String) {
        val userId = Session.currentUserId
        if (userId.isBlank()) return
        val requestId = listOf(userId, contactUserId).sorted().joinToString("_") + "_request"
        val request = hashMapOf(
            "from" to userId,
            "to" to contactUserId,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("friend_requests").document(requestId).set(request)
        _searchResults.value = _searchResults.value.filter { it != contactUserId }
    }

    private fun listenForRequests() {
        val userId = Session.currentUserId
        if (userId.isBlank()) return
        requestsListener?.remove()
        requestsListener = db.collection("friend_requests")
            .whereEqualTo("to", userId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    FriendRequest(
                        id = doc.id,
                        from = doc.getString("from") ?: return@mapNotNull null,
                        to = doc.getString("to") ?: return@mapNotNull null,
                        status = doc.getString("status") ?: "pending"
                    )
                } ?: emptyList()
                _incomingRequests.value = list
            }
    }

    fun acceptRequest(request: FriendRequest) {
        val userId = Session.currentUserId
        if (userId.isBlank()) return
        db.runBatch { batch ->
            batch.update(db.collection("users").document(userId), "contacts", FieldValue.arrayUnion(request.from))
            batch.update(db.collection("users").document(request.from), "contacts", FieldValue.arrayUnion(userId))
            batch.delete(db.collection("friend_requests").document(request.id))
        }
        _incomingRequests.value = _incomingRequests.value.filter { it.id != request.id }
    }

    fun rejectRequest(request: FriendRequest) {
        db.collection("friend_requests").document(request.id).delete()
        _incomingRequests.value = _incomingRequests.value.filter { it.id != request.id }
    }
}
