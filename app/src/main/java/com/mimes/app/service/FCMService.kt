package com.mimes.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mimes.app.MiMesApp
import com.mimes.app.MainActivity
import com.mimes.app.R
import com.mimes.app.ui.auth.Session

class FCMService : FirebaseMessagingService() {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FCMService"
        const val ACTION_ACCEPT_CALL = "com.mimes.app.action.ACCEPT_CALL"
        const val ACTION_REJECT_CALL = "com.mimes.app.action.REJECT_CALL"
        const val EXTRA_TARGET = "target"
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_PEER_NAME = "peerName"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_ID = "callerId"
        const val EXTRA_IS_VIDEO = "isVideo"
        const val EXTRA_AUTO_ACCEPT = "autoAccept"
        private const val NOTIFICATION_ID_MESSAGE = 1001
        private const val NOTIFICATION_ID_CALL = 2001
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        saveTokenToFirestore(token)
    }

    /** Сохраняет токен устройства в Firestore, если пользователь залогинен. */
    fun saveTokenToFirestore(token: String) {
        val userId = currentUserId()
        if (userId.isBlank()) {
            Log.d(TAG, "No active session, token not saved")
            return
        }
        db.collection("users").document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener { Log.d(TAG, "FCM token saved for $userId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save FCM token", e) }
    }

    private fun currentUserId(): String {
        if (Session.currentUserId.isNotBlank()) return Session.currentUserId
        val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        return prefs.getString("login", "") ?: ""
    }

    /** Выбирает канал уведомлений согласно настройкам пользователя (звук/вибрация). */
    private fun channelFor(isCall: Boolean): String {
        val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val sound = settings.getBoolean("notify_sound", true)
        val vibration = settings.getBoolean("notify_vibration", true)

        return when {
            !sound -> if (isCall) MiMesApp.CHANNEL_CALLS_SILENT else MiMesApp.CHANNEL_MESSAGES_SILENT
            !vibration -> if (isCall) MiMesApp.CHANNEL_CALLS_NO_VIB else MiMesApp.CHANNEL_MESSAGES_NO_VIB
            else -> if (isCall) MiMesApp.CHANNEL_CALLS else MiMesApp.CHANNEL_MESSAGES
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) {
            Log.d(TAG, "Empty data payload")
            return
        }
        Log.d(TAG, "FCM message received: type=${data["type"]}")

        when (data["type"]) {
            "call" -> showCallNotification(data)
            else -> showMessageNotification(data)
        }
    }

    /** Показывает уведомление о новом сообщении; тап ведёт в чат. */
    private fun showMessageNotification(data: Map<String, String>) {
        val chatId = data[EXTRA_CHAT_ID] ?: return
        val peerName = data[EXTRA_PEER_NAME] ?: return
        val senderId = data["senderId"] ?: return
        val text = data["text"] ?: ""

        // Не показываем уведомление о собственных сообщениях
        if (senderId == currentUserId()) return

        val title = data["title"] ?: peerName
        val body = if (text.isNotBlank()) text else "📎 Вложение"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = Intent.ACTION_MAIN
            putExtra(EXTRA_TARGET, "chat")
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_PEER_NAME, peerName)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_MESSAGE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelFor(isCall = false))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)
            .build()

        notifySafely(NOTIFICATION_ID_MESSAGE, notification)
    }

    /** Показывает уведомление о входящем звонке с кнопками «Принять» / «Отклонить». */
    private fun showCallNotification(data: Map<String, String>) {
        val callId = data[EXTRA_CALL_ID] ?: return
        val callerId = data[EXTRA_CALLER_ID] ?: return
        val isVideo = data[EXTRA_IS_VIDEO] == "true"

        if (callerId == currentUserId()) return

        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = ACTION_ACCEPT_CALL
            putExtra(EXTRA_TARGET, "call")
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_IS_VIDEO, isVideo)
            putExtra(EXTRA_AUTO_ACCEPT, true)
        }
        val acceptPending = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_CALL,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = ACTION_REJECT_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val rejectPending = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID_CALL + 1,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callType = if (isVideo) "видеозвонок" else "аудиозвонок"
        val notification = NotificationCompat.Builder(this, channelFor(isCall = true))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Входящий звонок")
            .setContentText("$callerId — входящий $callType")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(acceptPending, true)
            .setContentIntent(acceptPending)
            .addAction(0, "Принять", acceptPending)
            .addAction(R.drawable.ic_notification, "Отклонить", rejectPending)
            .build()

        notifySafely(NOTIFICATION_ID_CALL, notification)
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }
}
