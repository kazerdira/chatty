package com.chatty.domain.repository

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRoomRepository {
    suspend fun createRoom(
        name: String,
        type: ChatRoom.RoomType,
        participantIds: List<User.UserId>
    ): Result<ChatRoom>
    suspend fun getRoom(roomId: ChatRoom.RoomId): ChatRoom?
    suspend fun getRooms(): Result<List<ChatRoom>>
    fun observeRooms(): Flow<List<ChatRoom>>
    suspend fun joinRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<ChatRoom>
    suspend fun leaveRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<Unit>
    suspend fun updateRoom(roomId: ChatRoom.RoomId, name: String): Result<ChatRoom>
    suspend fun deleteRoom(roomId: ChatRoom.RoomId): Result<Unit>
}
