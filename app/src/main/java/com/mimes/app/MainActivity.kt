package com.mimes.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.mimes.app.data.DataInitializer
import com.mimes.app.service.FCMService
import com.mimes.app.ui.auth.Session
import com.mimes.app.ui.navigation.IncomingCallInfo
import com.mimes.app.ui.navigation.NavigationGraph
import com.mimes.app.ui.navigation.Screen
import com.mimes.app.ui.theme.MiMesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        restoreSession()
        DataInitializer.seedAdminUser()
        requestNotificationPermission()

        val loggedIn = Session.isLoggedIn
        val startDestination = if (loggedIn) Screen.ChatList.route else Screen.Auth.route

        val target = intent.getStringExtra(FCMService.EXTRA_TARGET)
        val chatId = intent.getStringExtra(FCMService.EXTRA_CHAT_ID)
        val peerName = intent.getStringExtra(FCMService.EXTRA_PEER_NAME)
        val callId = intent.getStringExtra(FCMService.EXTRA_CALL_ID)
        val callerId = intent.getStringExtra(FCMService.EXTRA_CALLER_ID)
        val isVideo = intent.getBooleanExtra(FCMService.EXTRA_IS_VIDEO, false)
        val autoAccept = intent.getBooleanExtra(FCMService.EXTRA_AUTO_ACCEPT, false)

        setContent {
            MiMesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavigationGraph(
                        navController = navController,
                        startDestination = startDestination,
                        openChat = if (loggedIn && target == "chat" && !chatId.isNullOrBlank() && !peerName.isNullOrBlank())
                            chatId to peerName else null,
                        incomingCall = if (loggedIn && target == "call" && !callId.isNullOrBlank() && !callerId.isNullOrBlank())
                            IncomingCallInfo(callerId, callId, isVideo, autoAccept) else null
                    )
                }
            }
        }
    }

    private fun restoreSession() {
        val saved = getSharedPreferences("auth", MODE_PRIVATE).getString("login", "")
        if (!saved.isNullOrBlank()) {
            Session.currentUserId = saved
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}
