package com.chatty.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val expiresIn: Long = 3600000
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)
