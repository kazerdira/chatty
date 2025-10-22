package com.chatty.data.repository

import com.chatty.data.local.TokenManager
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.dto.WebSocketMessage
import com.chatty.data.remote.dto.toEntity
import com.chatty.database.ChatDatabase
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import com.chatty.domain.repository.ChatRoomRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatRoomRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val database: ChatDatabase,
    private val tokenManager: TokenManager,
    private val scope: CoroutineScope
) : ChatRoomRepository {
    
    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    
    init {
        // Listen for WebSocket messages
        scope.launch {
            apiClient.incomingMessages.collect { message ->
                when (message) {
                    is WebSocketMessage.NewRoom -> {
                        // Add new room to the list
                        val newRoom = message.room.toEntity()
                        val currentRooms = _rooms.value
                        if (currentRooms.none { it.id == newRoom.id }) {
                            _rooms.value = currentRooms + newRoom
                            println("✅ New room received via WebSocket: ${newRoom.name}")
                        }
                    }
                    else -> {} // Handle other message types elsewhere
                }
            }
        }
    }
    
    override suspend fun createRoom(
        name: String,
        type: ChatRoom.RoomType,
        participantIds: List<User.UserId>
    ): Result<ChatRoom> {
        val typeString = when (type) {
            ChatRoom.RoomType.DIRECT -> "DIRECT"
            ChatRoom.RoomType.GROUP -> "GROUP"
            ChatRoom.RoomType.CHANNEL -> "CHANNEL"
        }
        
        return apiClient.createRoom(
            name = name,
            type = typeString,
            participantIds = participantIds.map { it.value }
        ).map { dto ->
            val room = dto.toEntity()
            // Add to local cache
            _rooms.value = _rooms.value + room
            room
        }
    }
    
    override suspend fun getRoom(roomId: ChatRoom.RoomId): ChatRoom? {
        return _rooms.value.find { it.id == roomId }
    }
    
    override suspend fun getRooms(): Result<List<ChatRoom>> {
        return apiClient.getRooms()
            .map { dtos -> dtos.map { it.toEntity() } }
            .onSuccess { rooms ->
                _rooms.value = rooms
            }
    }
    
    override fun observeRooms(): Flow<List<ChatRoom>> {
        return _rooms.asStateFlow()
    }
    
    override suspend fun joinRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<ChatRoom> {
        return runCatching {
            // TODO: Implement API endpoint
            throw NotImplementedError("Join room not yet implemented on backend")
        }
    }
    
    override suspend fun leaveRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<Unit> {
        return runCatching {
            // TODO: Implement API endpoint
            throw NotImplementedError("Leave room not yet implemented on backend")
        }
    }
    
    override suspend fun updateRoom(roomId: ChatRoom.RoomId, name: String): Result<ChatRoom> {
        return runCatching {
            // TODO: Implement API endpoint
            throw NotImplementedError("Update room not yet implemented on backend")
        }
    }
    
    override suspend fun deleteRoom(roomId: ChatRoom.RoomId): Result<Unit> {
        return runCatching {
            // TODO: Implement API endpoint
            throw NotImplementedError("Delete room not yet implemented on backend")
        }
    }
}
