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
    val error: String? = null
)

class ChatListViewModel(
    private val observeRoomsUseCase: ObserveRoomsUseCase,
    private val apiClient: ChatApiClient,
    private val roomRepository: ChatRoomRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    init {
        // Ensure WebSocket is connected
        viewModelScope.launch {
            println("🔌 ChatListViewModel: Checking WebSocket connection...")
            
            // Wait a bit for tokens to be available
            delay(300)
            
            if (apiClient.connectionState.value != WebSocketConnectionState.CONNECTED) {
                println("⚠️ ChatListViewModel: WebSocket not connected, connecting...")
                apiClient.connectWebSocket()
                
                // Wait for connection with timeout
                var attempts = 0
                while (apiClient.connectionState.value != WebSocketConnectionState.CONNECTED && attempts < 10) {
                    delay(500)
                    attempts++
                }
                
                if (apiClient.connectionState.value == WebSocketConnectionState.CONNECTED) {
                    println("✅ ChatListViewModel: WebSocket connected successfully")
                } else {
                    println("⚠️ ChatListViewModel: WebSocket connection timeout, will use polling")
                }
            } else {
                println("✅ ChatListViewModel: WebSocket already connected")
            }
        }
        
        // Load rooms
        loadRooms()
        
        // Start polling fallback (every 15 seconds)
        startPolling()
    }
    
    private fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // First, fetch from server to get latest
                roomRepository.getRooms()
                    .onSuccess { println("✅ Fetched ${it.size} rooms from server") }
                    .onFailure { println("⚠️ Failed to fetch rooms from server: ${it.message}") }
                
                // Then observe local changes
                observeRoomsUseCase()
                    .collect { rooms ->
                        println("📊 ChatListViewModel: Received ${rooms.size} rooms")
                        _uiState.value = _uiState.value.copy(
                            rooms = rooms.sortedByDescending { it.updatedAt },
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (error: Exception) {
                println("❌ ChatListViewModel: Error loading rooms - ${error.message}")
                error.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to load chats"
                )
            }
        }
    }
    
    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(15_000) // Poll every 15 seconds
                
                // Only poll if WebSocket is not connected
                if (apiClient.connectionState.value != WebSocketConnectionState.CONNECTED) {
                    println("🔄 ChatListViewModel: Polling for room updates (WebSocket disconnected)")
                    refreshRooms()
                }
            }
        }
    }
    
    private fun refreshRooms() {
        viewModelScope.launch {
            roomRepository.getRooms()
                .onSuccess { rooms ->
                    println("✅ ChatListViewModel: Refreshed ${rooms.size} rooms")
                }
                .onFailure { error ->
                    println("⚠️ ChatListViewModel: Refresh failed - ${error.message}")
                }
        }
    }
    
    fun retry() {
        loadRooms()
    }
    
    fun manualRefresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            refreshRooms()
            delay(500)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
