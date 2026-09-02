package com.mimes.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.mimes.app.rtc.RtcManager

/**
 * Обрабатывает действия с уведомлением о входящем звонке,
 * когда приложение находится в фоне или не запущено.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FCMService.ACTION_REJECT_CALL -> {
                val callId = intent.getStringExtra(FCMService.EXTRA_CALL_ID)
                Log.d(TAG, "Rejecting call: $callId")
                if (!callId.isNullOrBlank()) {
                    FirebaseFirestore.getInstance()
                        .collection("calls")
                        .document(callId)
                        .delete()
                }
            }
        }
    }

    companion object {
        private const val TAG = "CallActionReceiver"
    }
}
