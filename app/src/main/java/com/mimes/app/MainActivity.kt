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
import com.google.firebase.auth.FirebaseAuth
import com.mimes.app.ui.navigation.NavigationGraph
import com.mimes.app.ui.navigation.Screen
import com.mimes.app.ui.theme.MiMesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        
        enableEdgeToEdge()
        
        // Проверяем пользователя ПЕРЕД созданием контента
        val currentUser = auth.currentUser
        val startDestination = if (currentUser != null) {
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
