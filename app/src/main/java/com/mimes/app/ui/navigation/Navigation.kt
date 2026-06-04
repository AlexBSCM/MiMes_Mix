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
    object Call : Screen("call/{peerName}") {
        fun createRoute(peerName: String): String {
            return "call/${Uri.encode(peerName)}"
        }
    }
}

@Composable
fun NavigationGraph(navController: NavHostController, startDestination: String) {
    LaunchedEffect(Unit) {
        RtcManager.initialize(com.mimes.app.MiMesApp.instance)
        RtcManager.incomingCallFlow.collect { (callerId, callId) ->
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Call.route && currentRoute != "incoming_call/{callerId}/{callId}") {
                navController.navigate("incoming_call/${Uri.encode(callerId)}/${Uri.encode(callId)}")
            }
        }
    }
    // Also ensure listener is active on first composition (will be restarted by ChatListScreen after login)
    LaunchedEffect(Unit) {
        RtcManager.listenForIncomingCalls()
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
                        Log.d(NAV_TAG, "Audio call to ")
                        val encodedPeer = Uri.encode(peerName.replace("@", ""))
                        navController.navigate(Screen.Call.createRoute(encodedPeer))
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
            arguments = listOf(navArgument("peerName") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerName = backStackEntry.arguments?.getString("peerName") ?: ""
            CallScreen(
                peerName = peerName,
                onEndCall = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "incoming_call/{callerId}/{callId}",
            arguments = listOf(
                navArgument("callerId") { type = NavType.StringType },
                navArgument("callId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val callerId = backStackEntry.arguments?.getString("callerId") ?: ""
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            CallScreen(
                peerName = callerId,
                isIncoming = true,
                incomingCallId = callId,
                onEndCall = {
                    navController.popBackStack(Screen.ChatList.route, inclusive = false)
                }
            )
        }
    }
}
