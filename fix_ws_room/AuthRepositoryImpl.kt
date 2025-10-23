package com.chatty.data.repository

import com.chatty.data.local.TokenManager
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.dto.AuthRequest
import com.chatty.data.remote.dto.RefreshTokenRequest
import com.chatty.data.remote.dto.RegisterRequest
import com.chatty.domain.repository.AuthRepository
import com.chatty.domain.repository.AuthTokens
import kotlinx.coroutines.delay

class AuthRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val tokenManager: TokenManager
) : AuthRepository {
    
    override suspend fun login(username: String, password: String): Result<AuthTokens> {
        return apiClient.login(AuthRequest(username, password)).mapCatching { response ->
            println("💾 AuthRepository: Saving tokens for user: ${response.userId}")
            
            // Save tokens synchronously
            tokenManager.saveAccessToken(response.token)
            tokenManager.saveRefreshToken(response.refreshToken)
            tokenManager.saveUserId(response.userId)
            
            println("✅ AuthRepository: Tokens saved for user: ${response.userId}")
            
            // Give tokens time to persist (especially important for encrypted storage)
            delay(500)
            
            println("🔌 AuthRepository: Connecting WebSocket...")
            // Connect WebSocket after tokens are persisted
            apiClient.connectWebSocket()
            
            AuthTokens(
                accessToken = response.token,
                refreshToken = response.refreshToken,
                expiresIn = response.expiresIn
            )
        }
    }
    
    override suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String
    ): Result<AuthTokens> {
        return apiClient.register(RegisterRequest(username, email, password, displayName))
            .mapCatching { response ->
                println("💾 AuthRepository: Saving tokens after registration for user: ${response.userId}")
                
                // Save tokens synchronously
                tokenManager.saveAccessToken(response.token)
                tokenManager.saveRefreshToken(response.refreshToken)
                tokenManager.saveUserId(response.userId)
                
                println("✅ AuthRepository: Tokens saved after registration for user: ${response.userId}")
                
                // Give tokens time to persist (especially important for encrypted storage)
                delay(500)
                
                println("🔌 AuthRepository: Connecting WebSocket...")
                // Connect WebSocket after tokens are persisted
                apiClient.connectWebSocket()
                
                AuthTokens(
                    accessToken = response.token,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresIn
                )
            }
    }
    
    override suspend fun refreshToken(refreshToken: String): Result<AuthTokens> {
        return apiClient.refreshToken(RefreshTokenRequest(refreshToken))
            .mapCatching { response ->
                tokenManager.saveAccessToken(response.token)
                tokenManager.saveRefreshToken(response.refreshToken)
                
                // Give tokens time to persist
                delay(200)
                
                AuthTokens(
                    accessToken = response.token,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresIn
                )
            }
    }
    
    override suspend fun logout(): Result<Unit> {
        return runCatching {
            println("🚪 AuthRepository: Logging out...")
            apiClient.disconnectWebSocket()
            tokenManager.clearTokens()
            println("✅ AuthRepository: Logout complete")
        }
    }
    
    override fun isAuthenticated(): Boolean {
        return true // Simplified for now
    }
    
    override suspend fun getAccessToken(): String? {
        return tokenManager.getAccessToken()
    }
}
