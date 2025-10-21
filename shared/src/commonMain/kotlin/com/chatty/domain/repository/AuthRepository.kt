package com.chatty.domain.repository

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<AuthTokens>
    suspend fun register(username: String, password: String, displayName: String): Result<AuthTokens>
    suspend fun refreshToken(refreshToken: String): Result<AuthTokens>
    suspend fun logout(): Result<Unit>
    fun isAuthenticated(): Boolean
    suspend fun getAccessToken(): String?
}

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)
