package com.chatty.data.repository

import com.chatty.data.local.TokenManager
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.dto.AuthRequest
import com.chatty.data.remote.dto.RefreshTokenRequest
import com.chatty.data.remote.dto.RegisterRequest
import com.chatty.domain.repository.AuthRepository
import com.chatty.domain.repository.AuthTokens

class AuthRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val tokenManager: TokenManager
) : AuthRepository {
    
    override suspend fun login(username: String, password: String): Result<AuthTokens> {
        return apiClient.login(AuthRequest(username, password)).mapCatching { response ->
            tokenManager.saveAccessToken(response.token)
            tokenManager.saveRefreshToken(response.refreshToken)
            
            // Connect WebSocket after login
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
                tokenManager.saveAccessToken(response.token)
                tokenManager.saveRefreshToken(response.refreshToken)
                
                // Connect WebSocket after registration
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
                
                AuthTokens(
                    accessToken = response.token,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresIn
                )
            }
    }
    
    override suspend fun logout(): Result<Unit> {
        return runCatching {
            apiClient.disconnectWebSocket()
            tokenManager.clearTokens()
        }
    }
    
    override fun isAuthenticated(): Boolean {
        // This should be called synchronously, so we can't use suspend
        // In a real app, you'd check token expiration
        return true // Simplified for now
    }
    
    override suspend fun getAccessToken(): String? {
        return tokenManager.getAccessToken()
    }
}
