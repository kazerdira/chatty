package com.chatty.data.remote.dto

import com.chatty.domain.model.Message
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class WebSocketMessage {
    @Serializable
    data class Connected(
        val userId: String,
        val timestamp: Instant
    ) : WebSocketMessage()
    
    @Serializable
    data class SendMessage(
        val roomId: String,
        val content: MessageContentDto,
        val replyToId: String? = null,
        val tempId: String
    ) : WebSocketMessage()
    
    @Serializable
    data class MessageReceived(
        val message: MessageDto
    ) : WebSocketMessage()
    
    @Serializable
    data class MessageStatusUpdate(
        val messageId: String,
        val status: Message.MessageStatus,
        val userId: String
    ) : WebSocketMessage()
    
    @Serializable
    data class TypingIndicator(
        val roomId: String,
        val isTyping: Boolean
    ) : WebSocketMessage()
    
    @Serializable
    data class UserTyping(
        val roomId: String,
        val userId: String,
        val username: String,
        val isTyping: Boolean
    ) : WebSocketMessage()
    
    @Serializable
    data class MarkAsRead(
        val messageIds: List<String>
    ) : WebSocketMessage()
    
    @Serializable
    data class JoinRoom(
        val roomId: String
    ) : WebSocketMessage()
    
    @Serializable
    data class LeaveRoom(
        val roomId: String
    ) : WebSocketMessage()
    
    @Serializable
    data class UserStatusUpdate(
        val userId: String,
        val status: String
    ) : WebSocketMessage()
    
    @Serializable
    data class Error(
        val code: String,
        val message: String
    ) : WebSocketMessage()
}
