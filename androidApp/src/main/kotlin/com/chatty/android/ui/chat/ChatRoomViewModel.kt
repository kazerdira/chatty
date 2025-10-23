package com.chatty.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import com.chatty.domain.model.User
import com.chatty.domain.repository.UserRepository
import com.chatty.domain.usecase.GetMessagesUseCase
import com.chatty.domain.usecase.JoinRoomUseCase
import com.chatty.domain.usecase.ObserveMessagesUseCase
import com.chatty.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class ChatRoomUiState(
    val messages: List<Message> = emptyList(),
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

class ChatRoomViewModel(
    private val roomId: String,
    private val sendMessageUseCase: SendMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val userRepository: UserRepository,
    private val joinRoomUseCase: JoinRoomUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()
    
    private val chatRoomId = ChatRoom.RoomId(roomId)
    
    init {
        loadCurrentUser()
        joinRoom()
        observeMessages()
        loadInitialMessages()
    }
    
    private fun joinRoom() {
        viewModelScope.launch {
            println("🚪 Joining room: $roomId")
            joinRoomUseCase(chatRoomId)
        }
    }
    
    private fun loadCurrentUser() {
        viewModelScope.launch {
            val userId = userRepository.getCurrentUserId()
            _uiState.value = _uiState.value.copy(currentUserId = userId)
        }
    }
    
    fun isOwnMessage(message: Message): Boolean {
        val currentUserId = _uiState.value.currentUserId ?: return false
        return message.senderId.value == currentUserId
    }
    
    private fun observeMessages() {
        viewModelScope.launch {
            observeMessagesUseCase(chatRoomId)
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        isLoading = false
                    )
                }
        }
    }
    
    private fun loadInitialMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            getMessagesUseCase(chatRoomId, limit = 50)
                .onSuccess { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load messages"
                    )
                }
        }
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            
            val content = Message.MessageContent.Text(text)
            
            // Get real current user ID
            val currentUserId = _uiState.value.currentUserId?.let { User.UserId(it) } 
                ?: run {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = "Not authenticated"
                    )
                    return@launch
                }
            
            // Create optimistic message with SENDING status
            val tempMessageId = Message.MessageId("temp-${System.currentTimeMillis()}")
            val optimisticMessage = Message(
                id = tempMessageId,
                roomId = chatRoomId,
                senderId = currentUserId,
                content = content,
                timestamp = Clock.System.now(),
                status = Message.MessageStatus.SENDING,
                editedAt = null,
                replyTo = null
            )
            
            // Add optimistic message to UI immediately
            val currentMessages = _uiState.value.messages
            _uiState.value = _uiState.value.copy(
                messages = currentMessages + optimisticMessage
            )
            
            val params = SendMessageUseCase.SendMessageParams(
                roomId = chatRoomId,
                senderId = currentUserId,
                content = content,
                replyTo = null
            )
            
            sendMessageUseCase(params)
                .onSuccess { realMessage ->
                    // Replace optimistic message with real one (status: SENT)
                    val updatedMessages = _uiState.value.messages.map { msg ->
                        if (msg.id == tempMessageId) realMessage else msg
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = updatedMessages,
                        isSending = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    // Mark message as FAILED
                    val updatedMessages = _uiState.value.messages.map { msg ->
                        if (msg.id == tempMessageId) {
                            msg.copy(status = Message.MessageStatus.FAILED)
                        } else msg
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = updatedMessages,
                        isSending = false,
                        error = error.message ?: "Failed to send message"
                    )
                }
        }
    }
    
    fun loadMoreMessages() {
        val oldestMessage = _uiState.value.messages.minByOrNull { it.timestamp }
        
        viewModelScope.launch {
            getMessagesUseCase(
                roomId = chatRoomId,
                before = oldestMessage?.id,
                limit = 50
            ).onSuccess { messages ->
                val currentMessages = _uiState.value.messages
                val newMessages = (messages + currentMessages).distinctBy { it.id }
                _uiState.value = _uiState.value.copy(messages = newMessages)
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun retryLoadMessages() {
        loadInitialMessages()
    }
    
    fun retrySendMessage(text: String) {
        sendMessage(text)
    }
}
