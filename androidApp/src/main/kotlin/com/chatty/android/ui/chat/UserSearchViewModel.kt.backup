package com.chatty.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.WebSocketConnectionState
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import com.chatty.domain.usecase.CreateRoomUseCase
import com.chatty.domain.usecase.SearchUsersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

data class UserSearchUiState(
    val users: List<User> = emptyList(),
    val selectedUsers: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val createdRoomId: String? = null,
    val roomName: String? = null,
    val error: String? = null
)

class UserSearchViewModel(
    private val searchUsersUseCase: SearchUsersUseCase,
    private val createRoomUseCase: CreateRoomUseCase,
    private val apiClient: ChatApiClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()
    
    fun searchUsers(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            searchUsersUseCase(query)
                .onSuccess { users ->
                    println("✅ UserSearchViewModel: Found ${users.size} users")
                    _uiState.value = _uiState.value.copy(
                        users = users,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    println("❌ UserSearchViewModel: Search failed: ${error.message}")
                    error.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to search users"
                    )
                }
        }
    }
    
    fun toggleUserSelection(user: User) {
        val currentSelection = _uiState.value.selectedUsers
        val newSelection = if (currentSelection.contains(user)) {
            currentSelection - user
        } else {
            currentSelection + user
        }
        _uiState.value = _uiState.value.copy(selectedUsers = newSelection)
    }
    
    fun createRoom(roomName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            
            try {
                println("🔌 UserSearchViewModel: Ensuring WebSocket is connected...")
                
                // Ensure WebSocket is connected
                if (!ensureWebSocketConnected()) {
                    throw Exception("Could not establish WebSocket connection. Please check your internet and try again.")
                }
                
                val selectedUsers = _uiState.value.selectedUsers
                if (selectedUsers.isEmpty()) {
                    throw Exception("Please select at least one user")
                }
                
                val roomType = if (selectedUsers.size == 1) {
                    ChatRoom.RoomType.DIRECT
                } else {
                    ChatRoom.RoomType.GROUP
                }
                
                println("📝 UserSearchViewModel: Creating ${roomType.name} room: $roomName")
                
                createRoomUseCase(
                    CreateRoomUseCase.CreateRoomParams(
                        name = roomName,
                        type = roomType,
                        participantIds = selectedUsers.map { it.id }
                    )
                ).onSuccess { room ->
                    println("✅ UserSearchViewModel: Room created: ${room.id.value}")
                    
                    // Wait a bit to ensure server processes the room
                    delay(500)
                    
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createdRoomId = room.id.value,
                        roomName = roomName,
                        error = null
                    )
                }.onFailure { error ->
                    println("❌ UserSearchViewModel: Room creation failed: ${error.message}")
                    error.printStackTrace()
                    throw error
                }
            } catch (e: Exception) {
                println("❌ UserSearchViewModel: Error: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.message ?: "Failed to create chat. Please try again."
                )
            }
        }
    }
    
    private suspend fun ensureWebSocketConnected(): Boolean {
        val currentState = apiClient.connectionState.value
        
        when (currentState) {
            WebSocketConnectionState.CONNECTED -> {
                println("✅ WebSocket already connected")
                return true
            }
            WebSocketConnectionState.CONNECTING, WebSocketConnectionState.RECONNECTING -> {
                println("⏳ WebSocket connecting, waiting...")
                // Wait for connection
                val connected = withTimeoutOrNull(10_000) {
                    apiClient.connectionState.first { it == WebSocketConnectionState.CONNECTED }
                    true
                } ?: false
                
                if (connected) {
                    println("✅ WebSocket connected successfully")
                    return true
                } else {
                    println("❌ WebSocket connection timeout")
                    return false
                }
            }
            else -> {
                println("🔌 WebSocket not connected, attempting connection...")
                apiClient.retryConnection()
                
                // Wait for connection with timeout
                val connected = withTimeoutOrNull(10_000) {
                    apiClient.connectionState.first { it == WebSocketConnectionState.CONNECTED }
                    true
                } ?: false
                
                if (connected) {
                    println("✅ WebSocket connected successfully")
                    // Give it a moment to stabilize
                    delay(500)
                    return true
                } else {
                    println("❌ WebSocket connection failed")
                    return false
                }
            }
        }
    }
    
    fun resetCreatedRoom() {
        _uiState.value = _uiState.value.copy(
            createdRoomId = null,
            roomName = null
        )
    }
    
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            users = emptyList(),
            isLoading = false,
            error = null
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun retrySearch(query: String) {
        searchUsers(query)
    }
}
