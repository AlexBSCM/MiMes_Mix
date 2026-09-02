package com.mimes.app.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mimes.app.rtc.CallScreen
import com.mimes.app.rtc.RtcManager
import com.mimes.app.ui.auth.AuthScreen
import com.mimes.app.ui.chat.ChatListScreen
import com.mimes.app.ui.chatdetail.ChatScreen
import android.net.Uri

private const val NAV_TAG = "Navigation"

/** Параметры входящего звонка, переданные из FCM-уведомления. */
data class IncomingCallInfo(
    val callerId: String,
    val callId: String,
    val isVideo: Boolean,
    val autoAccept: Boolean = false
)

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object ChatList : Screen("chat_list")
    object ChatDetail : Screen("chat/{chatId}/{peerName}") {
        fun createRoute(chatId: String, peerName: String): String {
            val encodedName = Uri.encode(peerName)
            val route = "chat/$chatId/$encodedName"
            Log.d(NAV_TAG, "Creating route: $route")
            return route
        }
    }
    object Call : Screen("call/{peerName}/{isVideo}") {
        fun createRoute(peerName: String, isVideo: Boolean = false): String {
            return "call/${Uri.encode(peerName)}/$isVideo"
        }
    }
    object IncomingCall : Screen("incoming_call/{callerId}/{callId}/{isVideo}/{autoAccept}") {
        fun createRoute(callerId: String, callId: String, isVideo: Boolean, autoAccept: Boolean = false): String {
            return "incoming_call/${Uri.encode(callerId)}/${Uri.encode(callId)}/$isVideo/$autoAccept"
        }
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    startDestination: String,
    openChat: Pair<String, String>? = null,
    incomingCall: IncomingCallInfo? = null
) {
    LaunchedEffect(Unit) {
        RtcManager.initialize(com.mimes.app.MiMesApp.instance)
        RtcManager.incomingCallFlow.collect { (callerId, callId, isVideo) ->
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Call.route && currentRoute != Screen.IncomingCall.route) {
                navController.navigate(Screen.IncomingCall.createRoute(callerId, callId, isVideo)) {
                    launchSingleTop = true
                }
            }
        }
    }

    // Навигация из FCM-уведомления при холодном старте приложения
    LaunchedEffect(openChat, incomingCall) {
        when {
            incomingCall != null -> navController.navigate(
                Screen.IncomingCall.createRoute(
                    incomingCall.callerId,
                    incomingCall.callId,
                    incomingCall.isVideo,
                    incomingCall.autoAccept
                )
            ) { launchSingleTop = true }
            openChat != null -> navController.navigate(
                Screen.ChatDetail.createRoute(openChat.first, openChat.second)
            )
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    Log.d(NAV_TAG, "Auth success, navigating to ChatList")
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ChatList.route) {
            Log.d(NAV_TAG, "ChatList screen loaded")
            ChatListScreen(
                onChatClick = { chatId, peerName ->
                    Log.d(NAV_TAG, "Chat clicked: ID=, Name=")
                    if (chatId.isNotBlank() && peerName.isNotBlank()) {
                        val route = Screen.ChatDetail.createRoute(chatId, peerName)
                        navController.navigate(route)
                    } else {
                        Log.e(NAV_TAG, "Error: Empty chatId or peerName!")
                    }
                },
                onProfileClick = {
                    Log.d(NAV_TAG, "Profile click (not implemented)")
                }
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            val peerName = backStackEntry.arguments?.getString("peerName")

            Log.d(NAV_TAG, "ChatDetail loaded with ID: , Name: ")

            if (chatId != null && peerName != null) {
                ChatScreen(
                    chatId = chatId,
                    peerName = peerName,
                    onBackClick = {
                        Log.d(NAV_TAG, "Back from chat")
                        navController.popBackStack()
                    },
                    onCallClick = {
                        Log.d(NAV_TAG, "Audio call to $peerName")
                        navController.navigate(Screen.Call.createRoute(peerName.removePrefix("@"), false))
                    },
                    onVideoCallClick = {
                        Log.d(NAV_TAG, "Video call to $peerName")
                        navController.navigate(Screen.Call.createRoute(peerName.removePrefix("@"), true))
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ошибка загрузки чата: missing data")
                }
            }
        }

        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("peerName") { type = NavType.StringType },
                navArgument("isVideo") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val peerName = backStackEntry.arguments?.getString("peerName") ?: ""
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            CallScreen(
                peerName = peerName,
                isVideo = isVideo,
                onEndCall = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.IncomingCall.route,
            arguments = listOf(
                navArgument("callerId") { type = NavType.StringType },
                navArgument("callId") { type = NavType.StringType },
                navArgument("isVideo") { type = NavType.BoolType },
                navArgument("autoAccept") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callerId = backStackEntry.arguments?.getString("callerId") ?: ""
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            val autoAccept = backStackEntry.arguments?.getBoolean("autoAccept") ?: false
            CallScreen(
                peerName = callerId,
                isIncoming = true,
                incomingCallId = callId,
                isVideo = isVideo,
                autoAccept = autoAccept,
                onEndCall = {
                    navController.popBackStack(Screen.ChatList.route, inclusive = false)
                }
            )
        }
    }
}
