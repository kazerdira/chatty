package com.chatty.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Messages sent FROM client TO server via WebSocket
 */
@Serializable
sealed class ClientWebSocketMessage {
    @Serializable
    data class Authenticate(val userId: String) : ClientWebSocketMessage()
    
    @Serializable
    data class JoinRoom(val roomId: String) : ClientWebSocketMessage()
    
    @Serializable
    data class SendMessage(
        val messageId: String,
        val roomId: String,
        val content: MessageContentDto
    ) : ClientWebSocketMessage()
    
    @Serializable
    data class TypingIndicator(
        val roomId: String,
        val isTyping: Boolean
    ) : ClientWebSocketMessage()
}
