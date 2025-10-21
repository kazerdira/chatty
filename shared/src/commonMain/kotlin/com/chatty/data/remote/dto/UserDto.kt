package com.chatty.data.remote.dto

import com.chatty.domain.model.User
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val status: User.UserStatus,
    val lastSeen: Instant,
    val createdAt: Instant
)

fun UserDto.toEntity(): User = User(
    id = User.UserId(id),
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    status = status,
    lastSeen = lastSeen,
    createdAt = createdAt
)
