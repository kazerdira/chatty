package com.chatty.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import com.chatty.domain.usecase.CreateRoomUseCase
import com.chatty.domain.usecase.SearchUsersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val createRoomUseCase: CreateRoomUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()
    
    fun searchUsers(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            searchUsersUseCase(query)
                .onSuccess { users ->
                    _uiState.value = _uiState.value.copy(
                        users = users,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
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
            
            val selectedUsers = _uiState.value.selectedUsers
            val roomType = if (selectedUsers.size == 1) {
                ChatRoom.RoomType.DIRECT
            } else {
                ChatRoom.RoomType.GROUP
            }
            
            createRoomUseCase(
                CreateRoomUseCase.CreateRoomParams(
                    name = roomName,
                    type = roomType,
                    participantIds = selectedUsers.map { it.id }
                )
            ).onSuccess { room ->
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdRoomId = room.id.value,
                    roomName = roomName,
                    error = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = error.message ?: "Failed to create chat"
                )
            }
        }
    }
    
    fun resetCreatedRoom() {
        _uiState.value = _uiState.value.copy(
            createdRoomId = null,
            roomName = null
        )
    }
}
