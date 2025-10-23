package com.chatty.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.WebSocketConnectionState
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.usecase.ObserveRoomsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ChatListUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatListViewModel(
    private val observeRoomsUseCase: ObserveRoomsUseCase,
    private val apiClient: ChatApiClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    init {
        // Wait for WebSocket to connect, then load rooms
        viewModelScope.launch {
            println("🔌 ChatListViewModel: Waiting for WebSocket connection...")
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Wait for WebSocket to be connected (timeout after 10 seconds)
            val connected = withTimeoutOrNull(10000) {
                apiClient.connectionState.first { it == WebSocketConnectionState.CONNECTED }
            } != null
            
            if (connected) {
                println("✅ ChatListViewModel: WebSocket connected, loading rooms...")
                loadRooms()
            } else {
                println("⚠️ ChatListViewModel: WebSocket connection timeout, loading rooms anyway...")
                // Load rooms even if WebSocket fails - we can still use HTTP API
                loadRooms()
            }
        }
    }
    
    private fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                observeRoomsUseCase()
                    .collect { rooms ->
                        println("📋 ChatListViewModel: Received ${rooms.size} rooms")
                        _uiState.value = _uiState.value.copy(
                            rooms = rooms,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (error: Exception) {
                println("❌ ChatListViewModel: Error loading rooms: ${error.message}")
                error.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to load chats"
                )
            }
        }
    }
    
    fun retry() {
        println("🔄 ChatListViewModel: Retrying room load...")
        loadRooms()
    }
}
