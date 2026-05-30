package com.firechat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FireChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Канал для сообщений
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Сообщения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
            }

            // Канал для звонков
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Звонки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Входящие аудио и видеозвонки"
                enableVibration(true)
            }

            // Канал для активного звонка (foreground service)
            val ongoingCallChannel = NotificationChannel(
                CHANNEL_ONGOING_CALL,
                "Активный звонок",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывается во время звонка"
            }

            manager.createNotificationChannels(
                listOf(messagesChannel, callsChannel, ongoingCallChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "firechat_messages"
        const val CHANNEL_CALLS = "firechat_calls"
        const val CHANNEL_ONGOING_CALL = "firechat_ongoing_call"
    }
}
