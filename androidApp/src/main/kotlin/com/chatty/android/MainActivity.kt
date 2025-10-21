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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatty.android.ui.auth.LoginScreen
import com.chatty.android.ui.auth.LoginViewModel
import com.chatty.android.ui.chat.ChatListScreen
import com.chatty.android.ui.chat.ChatRoomScreen
import com.chatty.android.ui.chat.UserSearchScreen
import com.chatty.android.ui.theme.ChattyTheme
import org.koin.androidx.compose.koinViewModel

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
    val loginViewModel: LoginViewModel = koinViewModel()
    val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
    
    // Handle logout navigation
    LaunchedEffect(loginState.isLoggedIn) {
        if (!loginState.isLoggedIn && navController.currentDestination?.route != "login") {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }
    
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
                    navController.navigate("userSearch")
                },
                onLogout = {
                    loginViewModel.logout()
                }
            )
        }
        
        // User search screen
        composable("userSearch") {
            UserSearchScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onCreateChat = { userIds, roomName ->
                    // Navigate to the newly created room
                    // The UserSearchViewModel will handle room creation
                    // For now, navigate back to chat list
                    navController.popBackStack()
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
