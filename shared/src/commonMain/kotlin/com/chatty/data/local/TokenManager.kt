package com.chatty.data.local

interface TokenManager {
    suspend fun saveAccessToken(token: String)
    suspend fun saveRefreshToken(token: String)
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun clearTokens()
    
    // User info storage
    suspend fun saveUserId(userId: String)
    suspend fun getUserId(): String?
    suspend fun saveUserInfo(userId: String, username: String, displayName: String)
    suspend fun getUsername(): String?
    suspend fun getDisplayName(): String?
}
