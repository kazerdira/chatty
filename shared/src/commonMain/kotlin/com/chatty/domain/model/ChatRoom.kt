package com.chatty.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoom(
    val id: RoomId,
    val name: String,
    val type: RoomType,
    val participants: List<User.UserId>,
    val lastMessage: Message?,
    val unreadCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val avatarUrl: String? = null
) {
    @Serializable
    @JvmInline
    value class RoomId(val value: String)
    
    @Serializable
    enum class RoomType {
        DIRECT, GROUP, CHANNEL
    }
}
