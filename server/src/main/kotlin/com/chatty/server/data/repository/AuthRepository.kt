package com.chatty.server.data.repository

import com.chatty.server.AuthResponse
import com.chatty.server.RegisterRequest
import com.chatty.server.data.DatabaseFactory.dbQuery
import com.chatty.server.data.tables.RefreshTokens
import com.chatty.server.data.tables.Users
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.*

class AuthRepository {
    
    private val algorithm = Algorithm.HMAC256(
        System.getenv("JWT_SECRET") ?: "chatty-dev-secret-change-in-production"
    )
    
    suspend fun authenticate(username: String, password: String): UserDto? = dbQuery {
        val user = Users
            .select { Users.username eq username }
            .map { it to it[Users.passwordHash] }
            .singleOrNull()
        
        if (user != null && BCrypt.checkpw(password, user.second)) {
            UserDto(
                id = user.first[Users.id].value.toString(),
                username = user.first[Users.username],
                displayName = user.first[Users.displayName],
                avatarUrl = user.first[Users.avatarUrl] ?: "",
                status = user.first[Users.status],
                lastSeen = user.first[Users.lastSeen].toString()
            )
        } else {
            null
        }
    }
    
    suspend fun register(request: RegisterRequest): AuthResponse? = dbQuery {
        // Check if username exists
        val existing = Users
            .select { Users.username eq request.username }
            .singleOrNull()
        
        if (existing != null) {
            return@dbQuery null
        }
        
        // Create user
        val userId = Users.insert {
            it[username] = request.username
            it[displayName] = request.displayName
            it[passwordHash] = BCrypt.hashpw(request.password, BCrypt.gensalt())
            it[status] = "ONLINE"
            it[lastSeen] = Instant.now()
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } get Users.id
        
        generateAuthResponse(userId.value.toString(), request.username, request.displayName)
    }
    
    suspend fun refreshToken(refreshToken: String): AuthResponse? = dbQuery {
        val token = RefreshTokens
            .select { 
                (RefreshTokens.token eq refreshToken) and
                (RefreshTokens.expiresAt greater Instant.now())
            }
            .firstOrNull() ?: return@dbQuery null
        
        val user = Users
            .select { Users.id eq token[RefreshTokens.userId] }
            .map {
                Triple(
                    it[Users.id].value.toString(),
                    it[Users.username],
                    it[Users.displayName]
                )
            }
            .firstOrNull() ?: return@dbQuery null
        
        generateAuthResponse(user.first, user.second, user.third)
    }
    
    private fun generateAuthResponse(userId: String, username: String, displayName: String): AuthResponse {
        val now = Instant.now()
        val tokenExpiryMillis = now.toEpochMilli() + 3600000 // 1 hour
        
        val token = JWT.create()
            .withSubject(userId)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date(tokenExpiryMillis))
            .sign(algorithm)
        
        val refreshToken = UUID.randomUUID().toString()
        val refreshExpiryMillis = now.toEpochMilli() + (30L * 24 * 60 * 60 * 1000) // 30 days
        
        // Store refresh token
        RefreshTokens.insert {
            it[this.userId] = UUID.fromString(userId)
            it[this.token] = refreshToken
            it[expiresAt] = Instant.ofEpochMilli(refreshExpiryMillis)
            it[createdAt] = now
        }
        
        return AuthResponse(
            token = token,
            refreshToken = refreshToken,
            userId = userId,
            username = username,
            displayName = displayName,
            expiresIn = 3600000
        )
    }
}

data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val status: String,
    val lastSeen: String
)
