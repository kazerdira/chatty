package com.chatty.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: UserId,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val status: UserStatus,
    val lastSeen: Instant,
    val createdAt: Instant
) {
    @Serializable
    @JvmInline
    value class UserId(val value: String)
    
    @Serializable
    enum class UserStatus {
        ONLINE, AWAY, OFFLINE
    }
}
