package com.chatty.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatty.android.ui.auth.LoginScreen
import com.chatty.android.ui.chat.ChatListScreen
import com.chatty.android.ui.chat.ChatRoomScreen
import com.chatty.android.ui.theme.ChattyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            ChattyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChattyApp()
                }
            }
        }
    }
}

@Composable
fun ChattyApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // Login screen
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("chatList") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        // Chat list screen
        composable("chatList") {
            ChatListScreen(
                onChatClick = { roomId ->
                    navController.navigate("chatRoom/$roomId")
                },
                onNewChatClick = {
                    // TODO: Navigate to new chat screen
                    // For now, create a test chat room
                    navController.navigate("chatRoom/test-room-123")
                }
            )
        }
        
        // Chat room screen
        composable(
            route = "chatRoom/{roomId}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            
            ChatRoomScreen(
                roomId = roomId,
                roomName = "Chat Room", // TODO: Get actual room name
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
