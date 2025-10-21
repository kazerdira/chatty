package com.chatty.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TypingIndicator(
    val roomId: ChatRoom.RoomId,
    val userId: User.UserId,
    val username: String,
    val timestamp: Instant
)
