package com.firechat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.firechat.app.ui.auth.AuthScreen
import com.firechat.app.ui.call.CallScreen
import com.firechat.app.ui.chat.ChatListScreen
import com.firechat.app.ui.chat.ChatScreen
import com.firechat.app.ui.profile.ProfileScreen

// Маршруты навигации
sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object ChatList : Screen("chat_list")
    object Chat : Screen("chat/{chatId}/{peerName}") {
        fun createRoute(chatId: String, peerName: String) = "chat/$chatId/$peerName"
    }
    object Call : Screen("call/{chatId}/{peerName}/{callType}/{isIncoming}") {
        fun createRoute(
            chatId: String,
            peerName: String,
            callType: String, // "audio" | "video"
            isIncoming: Boolean
        ) = "call/$chatId/$peerName/$callType/$isIncoming"
    }
    object Profile : Screen("profile")
}

@Composable
fun FireChatNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Экран авторизации
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        // Список чатов
        composable(Screen.ChatList.route) {
            ChatListScreen(
                onChatClick = { chatId, peerName ->
                    navController.navigate(Screen.Chat.createRoute(chatId, peerName))
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        // Экран переписки
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType }
            )
        ) { backStack ->
            val chatId = backStack.arguments?.getString("chatId") ?: ""
            val peerName = backStack.arguments?.getString("peerName") ?: ""
            ChatScreen(
                chatId = chatId,
                peerName = peerName,
                onBack = { navController.popBackStack() },
                onCallClick = { callType ->
                    navController.navigate(
                        Screen.Call.createRoute(chatId, peerName, callType, false)
                    )
                }
            )
        }

        // Экран звонка
        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType },
                navArgument("callType") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType }
            )
        ) { backStack ->
            val chatId = backStack.arguments?.getString("chatId") ?: ""
            val peerName = backStack.arguments?.getString("peerName") ?: ""
            val callType = backStack.arguments?.getString("callType") ?: "audio"
            val isIncoming = backStack.arguments?.getBoolean("isIncoming") ?: false
            CallScreen(
                chatId = chatId,
                peerName = peerName,
                callType = callType,
                isIncoming = isIncoming,
                onCallEnd = { navController.popBackStack() }
            )
        }

        // Профиль
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
