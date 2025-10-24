package com.chatty.data.repository

import com.chatty.data.local.TokenManager
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.WebSocketConnectionState
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

/**
 * ✅ ENHANCED: ChatRoomRepository with WebSocket diagnostics and auto-refresh fallback
 * 
 * New features:
 * 1. WebSocket connection monitoring
 * 2. Auto-refresh fallback (every 15s) when WebSocket is disconnected
 * 3. Better logging for debugging
 * 4. Duplicate prevention
 */
class ChatRoomRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val database: ChatDatabase,
    private val tokenManager: TokenManager,
    private val scope: CoroutineScope
) : ChatRoomRepository {
    
    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    private var lastFetchTime = 0L
    
    init {
        println("🔧 ChatRoomRepository: Initializing...")
        
        // Load rooms from server on startup
        scope.launch {
            println("🔄 ChatRoomRepository: Loading initial rooms from server...")
            getRooms().onSuccess { rooms ->
                println("✅ ChatRoomRepository: Loaded ${rooms.size} initial rooms")
            }.onFailure { error ->
                println("❌ ChatRoomRepository: Failed to load initial rooms: ${error.message}")
            }
        }
        
        // Listen for WebSocket real-time updates
        scope.launch {
            println("👂 ChatRoomRepository: Starting WebSocket message listener...")
            apiClient.incomingMessages.collect { message ->
                handleWebSocketMessage(message)
            }
        }
        
        // ✅ NEW: Auto-refresh fallback when WebSocket is disconnected
        startAutoRefreshFallback()
        
        // ✅ NEW: Monitor WebSocket connection status
        monitorWebSocketConnection()
    }
    
    /**
     * ✅ NEW: Auto-refresh rooms when WebSocket is disconnected
     * This ensures users always see new rooms even if WebSocket fails
     */
    private fun startAutoRefreshFallback() {
        scope.launch {
            while (true) {
                delay(15_000) // Check every 15 seconds
                
                val wsState = apiClient.connectionState.value
                val timeSinceLastFetch = System.currentTimeMillis() - lastFetchTime
                
                // Only refresh if:
                // 1. WebSocket is not connected AND
                // 2. It's been more than 15 seconds since last fetch
                if (wsState != WebSocketConnectionState.CONNECTED && timeSinceLastFetch > 15_000) {
                    println("🔄 ChatRoomRepository: WebSocket disconnected, auto-refreshing rooms...")
                    getRooms().onSuccess {
                        println("✅ Auto-refresh: Fetched ${it.size} rooms via HTTP fallback")
                    }.onFailure {
                        println("❌ Auto-refresh failed: ${it.message}")
                    }
                }
            }
        }
    }
    
    /**
     * ✅ NEW: Monitor WebSocket connection and log status changes
     */
    private fun monitorWebSocketConnection() {
        scope.launch {
            var lastState: WebSocketConnectionState? = null
            
            apiClient.connectionState.collect { state ->
                if (state != lastState) {
                    println("🔌 ChatRoomRepository: WebSocket state changed: $lastState → $state")
                    
                    // When WebSocket reconnects, sync rooms
                    if (state == WebSocketConnectionState.CONNECTED && 
                        lastState != WebSocketConnectionState.CONNECTED) {
                        println("🔄 ChatRoomRepository: WebSocket reconnected, syncing rooms...")
                        delay(1000) // Give server a moment
                        getRooms()
                    }
                    
                    lastState = state
                }
            }
        }
    }
    
    /**
     * Handles real-time WebSocket updates
     */
    private fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message) {
            is WebSocketMessage.NewRoom -> {
                println("📨 ChatRoomRepository: Received NewRoom via WebSocket")
                val newRoom = message.room.toEntity()
                
                // Check if room already exists
                val exists = _rooms.value.any { it.id == newRoom.id }
                if (exists) {
                    println("⚠️ ChatRoomRepository: Room ${newRoom.id.value} already exists, skipping")
                } else {
                    println("✅ ChatRoomRepository: Adding new room: ${newRoom.name} (${newRoom.id.value})")
                    addOrUpdateRoom(newRoom)
                }
            }
            is WebSocketMessage.NewMessage -> {
                println("📨 ChatRoomRepository: Received NewMessage for room ${message.message.roomId}")
                updateRoomLastMessage(message.message)
            }
            is WebSocketMessage.Connected -> {
                println("🔌 ChatRoomRepository: WebSocket connected: ${message.userId}")
            }
            is WebSocketMessage.AuthenticationSuccess -> {
                println("✅ ChatRoomRepository: WebSocket authenticated: ${message.userId}")
            }
            is WebSocketMessage.Error -> {
                println("❌ ChatRoomRepository: WebSocket error: ${message.message}")
            }
            else -> {
                println("📨 ChatRoomRepository: Received ${message::class.simpleName}")
            }
        }
    }
    
    /**
     * Adds or updates a room in the local cache with proper deduplication
     */
    private fun addOrUpdateRoom(room: ChatRoom) {
        val currentRooms = _rooms.value
        val existingIndex = currentRooms.indexOfFirst { it.id == room.id }
        
        val updatedRooms = if (existingIndex >= 0) {
            // Update existing room
            println("🔄 ChatRoomRepository: Updating existing room: ${room.name}")
            currentRooms.toMutableList().apply {
                set(existingIndex, room)
            }
        } else {
            // Add new room
            println("➕ ChatRoomRepository: Adding new room: ${room.name}")
            currentRooms + room
        }
        
        // Sort by updated timestamp (most recent first)
        _rooms.value = updatedRooms.sortedByDescending { it.updatedAt }
        
        println("📊 ChatRoomRepository: Total rooms in cache: ${_rooms.value.size}")
    }
    
    /**
     * Updates the last message of a room when a new message arrives
     */
    private fun updateRoomLastMessage(messageDto: com.chatty.data.remote.dto.MessageDto) {
        val currentRooms = _rooms.value
        val roomIndex = currentRooms.indexOfFirst { it.id.value == messageDto.roomId }
        
        if (roomIndex >= 0) {
            val room = currentRooms[roomIndex]
            val updatedRoom = room.copy(
                lastMessage = messageDto.toEntity(),
                updatedAt = messageDto.timestamp
            )
            
            val updatedRooms = currentRooms.toMutableList().apply {
                set(roomIndex, updatedRoom)
            }
            
            // Re-sort by updated timestamp
            _rooms.value = updatedRooms.sortedByDescending { it.updatedAt }
            println("📝 ChatRoomRepository: Updated last message for room: ${room.name}")
        } else {
            println("⚠️ ChatRoomRepository: Room ${messageDto.roomId} not found in cache")
        }
    }
    
    /**
     * Creates a room via HTTP API with optimistic update
     */
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
        
        println("📝 ChatRoomRepository: Creating room '$name' with ${participantIds.size} participants")
        
        return apiClient.createRoom(
            name = name,
            type = typeString,
            participantIds = participantIds.map { it.value }
        ).map { dto ->
            val room = dto.toEntity()
            
            println("✅ ChatRoomRepository: Room created on server: ${room.id.value}")
            println("👥 ChatRoomRepository: Participants: ${room.participants.joinToString { it.value }}")
            
            // Add to local cache immediately (optimistic)
            addOrUpdateRoom(room)
            
            room
        }
    }
    
    override suspend fun getRoom(roomId: ChatRoom.RoomId): ChatRoom? {
        // Try local cache first
        val cachedRoom = _rooms.value.find { it.id == roomId }
        if (cachedRoom != null) {
            return cachedRoom
        }
        
        return null
    }
    
    /**
     * Fetches all rooms from server via HTTP API
     */
    override suspend fun getRooms(): Result<List<ChatRoom>> {
        println("🌐 ChatRoomRepository: Fetching rooms from server...")
        
        return apiClient.getRooms()
            .map { dtos -> 
                val rooms = dtos.map { it.toEntity() }
                    .sortedByDescending { it.updatedAt }
                
                println("✅ ChatRoomRepository: Fetched ${rooms.size} rooms from server")
                
                // Update cache
                _rooms.value = rooms
                lastFetchTime = System.currentTimeMillis()
                
                rooms
            }
    }
    
    override fun observeRooms(): Flow<List<ChatRoom>> {
        println("👂 ChatRoomRepository: Starting room observation...")
        return _rooms.asStateFlow()
    }
    
    override suspend fun joinRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<ChatRoom> {
        return runCatching {
            throw NotImplementedError("Join room not yet implemented on backend")
        }
    }
    
    override suspend fun leaveRoom(roomId: ChatRoom.RoomId, userId: User.UserId): Result<Unit> {
        return runCatching {
            throw NotImplementedError("Leave room not yet implemented on backend")
        }
    }
    
    override suspend fun updateRoom(roomId: ChatRoom.RoomId, name: String): Result<ChatRoom> {
        return runCatching {
            throw NotImplementedError("Update room not yet implemented on backend")
        }
    }
    
    override suspend fun deleteRoom(roomId: ChatRoom.RoomId): Result<Unit> {
        return runCatching {
            throw NotImplementedError("Delete room not yet implemented on backend")
        }
    }
}
