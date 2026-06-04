package com.mimes.app.data

import com.google.firebase.firestore.FirebaseFirestore

object DataInitializer {

    fun seedAdminUser() {
        val db = FirebaseFirestore.getInstance()
        val adminRef = db.collection("users").document("@AVZ")
        adminRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                adminRef.set(
                    hashMapOf(
                        "password" to "666666",
                        "role" to "admin"
                    )
                )
            }
        }
    }

    fun resetChatData(onComplete: () -> Unit = {}) {
        val db = FirebaseFirestore.getInstance()
        db.collection("chats").get().addOnSuccessListener { chats ->
            val pending = (chats.size() + 1).coerceAtLeast(1)
            var completed = 0
            val checkDone = {
                completed++
                if (completed >= pending) onComplete()
            }
            for (chatDoc in chats.documents) {
                chatDoc.reference.collection("messages").get()
                    .addOnSuccessListener { messages ->
                        for (msgDoc in messages.documents) {
                            msgDoc.reference.delete()
                        }
                        checkDone()
                    }
                chatDoc.reference.delete()
                checkDone()
            }
            checkDone()
        }
    }
}
