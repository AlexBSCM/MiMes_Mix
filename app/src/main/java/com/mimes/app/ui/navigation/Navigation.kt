package com.mimes.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mimes.app.ui.auth.AuthScreen
import com.mimes.app.ui.chat.ChatListScreen

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object ChatList : Screen("chat_list")
    object ChatDetail : Screen("chat/{chatId}/{peerName}") {
        fun createRoute(chatId: String, peerName: String) = "chat//"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        
        // 1. Экран входа
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. Список чатов
        composable(Screen.ChatList.route) {
            ChatListScreen(
                onChatClick = { chatId, peerName ->
                    val route = Screen.ChatDetail.createRoute(chatId, peerName)
                    navController.navigate(route)
                },
                onProfileClick = { }
            )
        }

        // 3. ЭКРАН ЧАТА (Именно его не хватало!)
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerName = backStackEntry.arguments?.getString("peerName") ?: "Неизвестный собеседник"
            
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text(peerName) })
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Чат с: \n(Здесь будет переписка)")
                }
            }
        }
    }
}
