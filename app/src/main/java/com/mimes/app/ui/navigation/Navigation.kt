package com.mimes.app.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
}

@Composable
fun NavigationGraph(navController: NavHostController, startDestination: String) {
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
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ошибка загрузки чата: missing data")
                }
            }
        }
    }
}
