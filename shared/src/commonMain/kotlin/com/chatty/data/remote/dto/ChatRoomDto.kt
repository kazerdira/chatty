package com.chatty.data.remote.dto

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomDto(
    val id: String,
    val name: String,
    val type: ChatRoom.RoomType,
    val participants: List<String>,
    val lastMessage: MessageDto?,
    val unreadCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val avatarUrl: String? = null
)

@Serializable
data class CreateRoomRequest(
    val name: String,
    val type: String,
    val participantIds: List<String>
)

fun ChatRoomDto.toEntity(): ChatRoom = ChatRoom(
    id = ChatRoom.RoomId(id),
    name = name,
    type = type,
    participants = participants.map { User.UserId(it) },
    lastMessage = lastMessage?.toEntity(),
    unreadCount = unreadCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    avatarUrl = avatarUrl
)
