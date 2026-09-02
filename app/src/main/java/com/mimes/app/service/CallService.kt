package com.mimes.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mimes.app.MiMesApp
import com.mimes.app.MainActivity
import com.mimes.app.R

/**
 * Foreground Service, уведомляющий систему о том, что приложение использует
 * микрофон/камеру для активного звонка. Показывает постоянное уведомление
 * с кнопкой «Завершить».
 */
class CallService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 3001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = FCMService.ACTION_END_CALL
        }
        val endPending = PendingIntent.getBroadcast(
            this, 1, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MiMesApp.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Активный звонок")
            .setContentText("Звонок продолжается…")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openPending)
            .addAction(0, "Завершить", endPending)
            .build()
    }
}