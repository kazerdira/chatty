package com.chatty.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.WebSocketConnectionState
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.repository.ChatRoomRepository
import com.chatty.domain.usecase.ObserveRoomsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatListUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val wsConnectionState: WebSocketConnectionState = WebSocketConnectionState.DISCONNECTED // ✅ NEW: For debugging
)

/**
 * ✅ ENHANCED: ChatListViewModel with WebSocket monitoring and diagnostics
 * 
 * New features:
 * 1. Exposes WebSocket connection state to UI
 * 2. Aggressive connection retry on startup
 * 3. Better logging for debugging
 */
class ChatListViewModel(
    private val observeRoomsUseCase: ObserveRoomsUseCase,
    private val apiClient: ChatApiClient,
    private val roomRepository: ChatRoomRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    init {
        println("🏠 ChatListViewModel: Initializing...")
        
        // ✅ CRITICAL: Aggressively ensure WebSocket is connected
        ensureWebSocketConnectedOnStartup()
        
        // Monitor WebSocket state for UI
        monitorWebSocketState()
        
        // Load rooms from server
        loadRooms()
        
        // Smart fallback refresh
        startSmartFallbackRefresh()
    }
    
    /**
     * ✅ NEW: Aggressively ensure WebSocket connects on startup
     * This is critical for receiving real-time notifications
     */
    private fun ensureWebSocketConnectedOnStartup() {
        viewModelScope.launch {
            println("🔌 ChatListViewModel: Ensuring WebSocket connection on startup...")
            
            var attempts = 0
            while (attempts < 3 && apiClient.connectionState.value != WebSocketConnectionState.CONNECTED) {
                attempts++
                println("🔌 ChatListViewModel: Connection attempt $attempts/3")
                
                val currentState = apiClient.connectionState.value
                
                when (currentState) {
                    WebSocketConnectionState.DISCONNECTED,
                    WebSocketConnectionState.ERROR -> {
                        println("🔄 ChatListViewModel: Attempting WebSocket connection...")
                        apiClient.retryConnection()
                    }
                    WebSocketConnectionState.CONNECTING,
                    WebSocketConnectionState.RECONNECTING -> {
                        println("⏳ ChatListViewModel: WebSocket connecting, waiting...")
                    }
                    WebSocketConnectionState.CONNECTED -> {
                        println("✅ ChatListViewModel: WebSocket connected successfully!")
                        return@launch
                    }
                }
                
                // Wait before checking again
                delay(2000)
            }
            
            val finalState = apiClient.connectionState.value
            if (finalState != WebSocketConnectionState.CONNECTED) {
                println("⚠️ ChatListViewModel: WebSocket connection failed after 3 attempts")
                println("⚠️ ChatListViewModel: Current state: $finalState")
                println("ℹ️ ChatListViewModel: App will use HTTP fallback for updates")
            } else {
                println("✅ ChatListViewModel: WebSocket ready for real-time updates")
            }
        }
    }
    
    /**
     * ✅ NEW: Monitor WebSocket state and expose to UI
     */
    private fun monitorWebSocketState() {
        viewModelScope.launch {
            apiClient.connectionState.collect { state ->
                println("🔌 ChatListViewModel: WebSocket state: $state")
                _uiState.value = _uiState.value.copy(wsConnectionState = state)
                
                // If WebSocket connects/reconnects, refresh rooms
                if (state == WebSocketConnectionState.CONNECTED) {
                    println("🔄 ChatListViewModel: WebSocket connected, refreshing rooms...")
                    delay(1000) // Give server a moment
                    refreshRooms()
                }
            }
        }
    }
    
    /**
     * Loads rooms and observes real-time updates
     */
    private fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                println("👂 ChatListViewModel: Starting room observation...")
                observeRoomsUseCase()
                    .collect { rooms ->
                        println("📊 ChatListViewModel: Received ${rooms.size} rooms from repository")
                        _uiState.value = _uiState.value.copy(
                            rooms = rooms,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (error: Exception) {
                println("❌ ChatListViewModel: Error loading rooms: ${error.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to load chats"
                )
            }
        }
    }
    
    /**
     * Smart fallback: Only refresh when WebSocket is genuinely broken
     */
    private fun startSmartFallbackRefresh() {
        viewModelScope.launch {
            var disconnectedTime = 0L
            
            while (true) {
                delay(10_000) // Check every 10 seconds
                
                val state = apiClient.connectionState.value
                
                when (state) {
                    WebSocketConnectionState.CONNECTED -> {
                        // WebSocket working - reset timer
                        disconnectedTime = 0L
                    }
                    
                    WebSocketConnectionState.DISCONNECTED,
                    WebSocketConnectionState.ERROR -> {
                        disconnectedTime += 10_000
                        
                        // Only take action if disconnected for more than 30 seconds
                        if (disconnectedTime >= 30_000) {
                            println("⚠️ ChatList: WebSocket down for ${disconnectedTime/1000}s, attempting recovery...")
                            
                            // Try to reconnect WebSocket first
                            apiClient.retryConnection()
                            delay(5_000)
                            
                            // If still not connected, do HTTP refresh
                            if (apiClient.connectionState.value != WebSocketConnectionState.CONNECTED) {
                                println("🔄 ChatList: WebSocket recovery failed, using HTTP fallback")
                                refreshRooms()
                            }
                            
                            disconnectedTime = 0L
                        }
                    }
                    
                    else -> {
                        // Connecting/Reconnecting - give it time
                        disconnectedTime = 0L
                    }
                }
            }
        }
    }
    
    /**
     * Manual room refresh via HTTP (fallback mechanism)
     */
    private fun refreshRooms() {
        viewModelScope.launch {
            println("🔄 ChatListViewModel: Manual refresh requested")
            roomRepository.getRooms()
                .onSuccess { rooms ->
                    println("✅ ChatListViewModel: Refreshed ${rooms.size} rooms via HTTP")
                }
                .onFailure { error ->
                    println("❌ ChatListViewModel: Refresh failed: ${error.message}")
                }
        }
    }
    
    fun retry() {
        viewModelScope.launch {
            println("🔄 ChatList: User requested retry")
            
            // Force WebSocket reconnection
            apiClient.retryConnection()
            
            delay(2000)
            
            // Reload rooms
            refreshRooms()
        }
    }
}
