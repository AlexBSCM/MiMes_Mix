package com.mimes.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MiMesApp : Application() {
    companion object {
        lateinit var instance: MiMesApp
            private set

        const val CHANNEL_MESSAGES = "mimes_messages"
        const val CHANNEL_MESSAGES_NO_VIB = "mimes_messages_no_vibration"
        const val CHANNEL_MESSAGES_SILENT = "mimes_messages_silent"
        const val CHANNEL_CALLS = "mimes_calls"
        const val CHANNEL_CALLS_NO_VIB = "mimes_calls_no_vibration"
        const val CHANNEL_CALLS_SILENT = "mimes_calls_silent"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        // Канал со звуком и вибрацией (по умолчанию)
        createChannel(manager, CHANNEL_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = true)
        createChannel(manager, CHANNEL_CALLS, "Звонки", NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = true)

        // Канал со звуком, но без вибрации
        createChannel(manager, CHANNEL_MESSAGES_NO_VIB, "Сообщения (без вибрации)", NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = false)
        createChannel(manager, CHANNEL_CALLS_NO_VIB, "Звонки (без вибрации)", NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = false)

        // Тихий канал (без звука и вибрации)
        createChannel(manager, CHANNEL_MESSAGES_SILENT, "Сообщения (тихо)", NotificationManager.IMPORTANCE_LOW, sound = false, vibrate = false)
        createChannel(manager, CHANNEL_CALLS_SILENT, "Звонки (тихо)", NotificationManager.IMPORTANCE_LOW, sound = false, vibrate = false)
    }

    private fun createChannel(
        manager: NotificationManager,
        id: String,
        name: String,
        importance: Int,
        sound: Boolean,
        vibrate: Boolean
    ) {
        val channel = NotificationChannel(id, name, importance).apply {
            description = name
            enableVibration(vibrate)
            if (!sound) setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }
}
