package com.mimes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.mimes.app.data.DataInitializer
import com.mimes.app.ui.auth.Session
import com.mimes.app.ui.navigation.NavigationGraph
import com.mimes.app.ui.navigation.Screen
import com.mimes.app.ui.theme.MiMesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DataInitializer.seedAdminUser()

        val startDestination = if (Session.isLoggedIn) {
            Screen.ChatList.route
        } else {
            Screen.Auth.route
        }

        setContent {
            MiMesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavigationGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
