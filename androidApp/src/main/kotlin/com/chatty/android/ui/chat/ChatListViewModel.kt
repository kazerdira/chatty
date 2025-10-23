package com.chatty.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatty.data.remote.ChatApiClient
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.usecase.ObserveRoomsUseCase
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
    private val apiClient: ChatApiClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    init {
        // Connect WebSocket when entering chat list (in case user bypassed login with saved token)
        viewModelScope.launch {
            println("🔌 ChatListViewModel: Ensuring WebSocket is connected...")
            apiClient.connectWebSocket()
        }
        loadRooms()
    }
    
    private fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                observeRoomsUseCase()
                    .collect { rooms ->
                        _uiState.value = _uiState.value.copy(
                            rooms = rooms,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to load chats"
                )
            }
        }
    }
    
    fun retry() {
        loadRooms()
    }
}
