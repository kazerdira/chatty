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
import kotlinx.coroutines.delay

class ChatRoomRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val database: ChatDatabase,
    private val tokenManager: TokenManager,
    private val scope: CoroutineScope
) : ChatRoomRepository {
    
    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    
    init {
        println("🏗️ ChatRoomRepository: Initializing")
        
        // Load initial rooms from server
        scope.launch {
            delay(1000) // Give WebSocket time to connect
            println("🔄 ChatRoomRepository: Loading initial rooms")
            getRooms().onSuccess { rooms ->
                println("✅ ChatRoomRepository: Loaded ${rooms.size} initial rooms")
            }.onFailure { error ->
                println("⚠️ ChatRoomRepository: Failed to load initial rooms: ${error.message}")
            }
        }
        
        // Listen for WebSocket messages
        scope.launch {
            apiClient.incomingMessages.collect { message ->
                when (message) {
                    is WebSocketMessage.NewRoom -> {
                        println("📨 ChatRoomRepository: Received new room via WebSocket")
                        handleNewRoom(message.room.toEntity())
                    }
                    is WebSocketMessage.NewMessage -> {
                        println("📨 ChatRoomRepository: Received new message, updating room")
                        handleNewMessage(message.message.roomId)
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun handleNewRoom(room: ChatRoom) {
        val currentRooms = _rooms.value
        
        // Check if room already exists
        if (currentRooms.any { it.id == room.id }) {
            println("ℹ️ ChatRoomRepository: Room ${room.id.value} already exists, skipping")
            return
        }
        
        println("➕ ChatRoomRepository: Adding new room: ${room.name} (${room.id.value})")
        
        // Add to list and sort by most recent
        _rooms.value = (currentRooms + room).sortedByDescending { it.updatedAt }
    }
    
    private fun handleNewMessage(roomId: String) {
        // Trigger a refresh for this specific room to get updated lastMessage
        scope.launch {
            println("🔄 ChatRoomRepository: Refreshing room $roomId after new message")
            delay(500) // Small delay to ensure server has processed the message
            getRooms() // This will update the entire room list with latest messages
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
        
        println("📝 ChatRoomRepository: Creating room - name: $name, type: $typeString")
        
        return apiClient.createRoom(
            name = name,
            type = typeString,
            participantIds = participantIds.map { it.value }
        ).map { dto ->
            val room = dto.toEntity()
            println("✅ ChatRoomRepository: Room created successfully - ${room.id.value}")
            
            // Add to local cache immediately
            handleNewRoom(room)
            
            // Also trigger a server refresh to ensure consistency
            scope.launch {
                delay(1000) // Give server time to broadcast to other users
                getRooms()
            }
            
            room
        }.onFailure { error ->
            println("❌ ChatRoomRepository: Failed to create room: ${error.message}")
        }
    }
    
    override suspend fun getRoom(roomId: ChatRoom.RoomId): ChatRoom? {
        return _rooms.value.find { it.id == roomId }
    }
    
    override suspend fun getRooms(): Result<List<ChatRoom>> {
        println("🔄 ChatRoomRepository: Fetching rooms from server")
        
        return apiClient.getRooms()
            .map { dtos ->
                val rooms = dtos.map { it.toEntity() }
                    .sortedByDescending { it.updatedAt }
                
                println("✅ ChatRoomRepository: Fetched ${rooms.size} rooms")
                
                // Update local cache
                _rooms.value = rooms
                
                rooms
            }
            .onFailure { error ->
                println("❌ ChatRoomRepository: Failed to fetch rooms: ${error.message}")
            }
    }
    
    override fun observeRooms(): Flow<List<ChatRoom>> {
        return _rooms.asStateFlow()
    }
    
    override suspend fun joinRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<ChatRoom> {
        return runCatching {
            println("🚪 ChatRoomRepository: Joining room ${roomId.value}")
            
            // Send join message via WebSocket
            apiClient.joinRoom(roomId.value)
            
            // Get the room from cache
            getRoom(roomId) ?: throw Exception("Room not found: ${roomId.value}")
        }
    }
    
    override suspend fun leaveRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<Unit> {
        return runCatching {
            println("🚪 ChatRoomRepository: Leaving room ${roomId.value}")
            // TODO: Implement leave room API endpoint
            throw NotImplementedError("Leave room not yet implemented on backend")
        }
    }
    
    override suspend fun updateRoom(roomId: ChatRoom.RoomId, name: String): Result<ChatRoom> {
        return runCatching {
            println("✏️ ChatRoomRepository: Updating room ${roomId.value}")
            // TODO: Implement update room API endpoint
            throw NotImplementedError("Update room not yet implemented on backend")
        }
    }
    
    override suspend fun deleteRoom(roomId: ChatRoom.RoomId): Result<Unit> {
        return runCatching {
            println("🗑️ ChatRoomRepository: Deleting room ${roomId.value}")
            // TODO: Implement delete room API endpoint
            throw NotImplementedError("Delete room not yet implemented on backend")
        }
    }
}
