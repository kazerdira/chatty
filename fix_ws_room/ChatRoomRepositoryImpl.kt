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
import kotlinx.coroutines.delay
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
        // Load existing rooms from server on startup
        scope.launch {
            println("🔄 ChatRoomRepository: Loading rooms from server...")
            getRooms().onSuccess { rooms ->
                println("✅ ChatRoomRepository: Loaded ${rooms.size} rooms from server")
            }.onFailure { error ->
                println("❌ ChatRoomRepository: Failed to load rooms: ${error.message}")
            }
        }
        
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
                            println("✅ ChatRoomRepository: New room received via WebSocket: ${newRoom.name}")
                        } else {
                            println("ℹ️ ChatRoomRepository: Room ${newRoom.name} already exists, skipping")
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
        
        println("📝 ChatRoomRepository: Creating room '$name' of type $typeString")
        println("📝 ChatRoomRepository: Participants: ${participantIds.map { it.value }}")
        
        return apiClient.createRoom(
            name = name,
            type = typeString,
            participantIds = participantIds.map { it.value }
        ).map { dto ->
            val room = dto.toEntity()
            println("✅ ChatRoomRepository: Room created successfully: ${room.id.value}")
            
            // Add to local cache immediately for the creator
            val currentRooms = _rooms.value
            if (currentRooms.none { it.id == room.id }) {
                _rooms.value = currentRooms + room
                println("✅ ChatRoomRepository: Room added to local cache, total rooms: ${_rooms.value.size}")
            }
            
            // Refresh from server after a delay to ensure all participants get the room
            scope.launch {
                delay(1500) // Give server time to broadcast to other clients
                println("🔄 ChatRoomRepository: Refreshing rooms from server after creation...")
                getRooms().onSuccess { 
                    println("✅ ChatRoomRepository: Rooms refreshed, now have ${_rooms.value.size} rooms")
                }.onFailure {
                    println("⚠️ ChatRoomRepository: Failed to refresh rooms: ${it.message}")
                }
            }
            
            room
        }.onFailure { error ->
            println("❌ ChatRoomRepository: Failed to create room: ${error.message}")
            error.printStackTrace()
        }
    }
    
    override suspend fun getRoom(roomId: ChatRoom.RoomId): ChatRoom? {
        val room = _rooms.value.find { it.id == roomId }
        if (room == null) {
            println("⚠️ ChatRoomRepository: Room ${roomId.value} not found in cache")
        }
        return room
    }
    
    override suspend fun getRooms(): Result<List<ChatRoom>> {
        println("🔄 ChatRoomRepository: Fetching rooms from API...")
        return apiClient.getRooms()
            .map { dtos -> 
                val rooms = dtos.map { it.toEntity() }
                println("✅ ChatRoomRepository: Fetched ${rooms.size} rooms from API")
                rooms
            }
            .onSuccess { rooms ->
                _rooms.value = rooms
                println("✅ ChatRoomRepository: Updated local cache with ${rooms.size} rooms")
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
