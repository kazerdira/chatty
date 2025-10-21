package com.chatty.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class Message(
    val id: MessageId,
    val roomId: ChatRoom.RoomId,
    val senderId: User.UserId,
    val content: MessageContent,
    val timestamp: Instant,
    val status: MessageStatus,
    val editedAt: Instant? = null,
    val replyTo: MessageId? = null
) {
    @Serializable
    @JvmInline
    value class MessageId(val value: String)
    
    @Serializable
    sealed class MessageContent {
        @Serializable
        data class Text(val text: String) : MessageContent()
        
        @Serializable
        data class Image(
            val url: String,
            val thumbnailUrl: String,
            val width: Int? = null,
            val height: Int? = null
        ) : MessageContent()
        
        @Serializable
        data class Video(
            val url: String,
            val thumbnailUrl: String,
            val duration: Long // Duration in milliseconds
        ) : MessageContent()
        
        @Serializable
        data class File(
            val url: String,
            val fileName: String,
            val size: Long,
            val mimeType: String
        ) : MessageContent()
        
        @Serializable
        data class Voice(
            val url: String,
            val duration: Long // Duration in milliseconds
        ) : MessageContent()
    }
    
    @Serializable
    enum class MessageStatus {
        SENDING, SENT, DELIVERED, READ, FAILED
    }
}
