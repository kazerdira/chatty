package com.chatty.domain.usecase

import com.chatty.data.local.TokenManager
import com.chatty.data.remote.ChatApiClient

class LogoutUseCase(
    private val tokenManager: TokenManager,
    private val apiClient: ChatApiClient
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            println("🚪 LogoutUseCase: Starting logout process")
            
            // Step 1: Disconnect WebSocket
            println("🔌 LogoutUseCase: Disconnecting WebSocket")
            apiClient.disconnectWebSocket()
            
            // Step 2: Clear tokens
            println("🗑️ LogoutUseCase: Clearing tokens")
            tokenManager.clearTokens()
            
            println("✅ LogoutUseCase: Logout complete")
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ LogoutUseCase: Logout error - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
