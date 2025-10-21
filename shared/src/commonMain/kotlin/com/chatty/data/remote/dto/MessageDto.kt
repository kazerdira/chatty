package com.chatty.data.remote.dto

import com.chatty.domain.model.Message
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String?,
    val content: MessageContentDto,
    val timestamp: Instant,
    val status: Message.MessageStatus,
    val editedAt: Instant? = null,
    val replyTo: String? = null
)

@Serializable
sealed class MessageContentDto {
    @Serializable
    data class Text(val text: String) : MessageContentDto()
    
    @Serializable
    data class Image(
        val url: String,
        val thumbnailUrl: String,
        val width: Int? = null,
        val height: Int? = null
    ) : MessageContentDto()
    
    @Serializable
    data class Video(
        val url: String,
        val thumbnailUrl: String,
        val duration: Long
    ) : MessageContentDto()
    
    @Serializable
    data class File(
        val url: String,
        val fileName: String,
        val size: Long,
        val mimeType: String
    ) : MessageContentDto()
    
    @Serializable
    data class Voice(
        val url: String,
        val duration: Long
    ) : MessageContentDto()
}

// Mappers
fun MessageDto.toEntity(): Message = Message(
    id = Message.MessageId(id),
    roomId = ChatRoom.RoomId(roomId),
    senderId = User.UserId(senderId),
    content = content.toEntity(),
    timestamp = timestamp,
    status = status,
    editedAt = editedAt,
    replyTo = replyTo?.let { Message.MessageId(it) }
)

fun MessageContentDto.toEntity(): Message.MessageContent = when (this) {
    is MessageContentDto.Text -> Message.MessageContent.Text(text)
    is MessageContentDto.Image -> Message.MessageContent.Image(url, thumbnailUrl, width, height)
    is MessageContentDto.Video -> Message.MessageContent.Video(url, thumbnailUrl, duration)
    is MessageContentDto.File -> Message.MessageContent.File(url, fileName, size, mimeType)
    is MessageContentDto.Voice -> Message.MessageContent.Voice(url, duration)
}

fun Message.MessageContent.toDto(): MessageContentDto = when (this) {
    is Message.MessageContent.Text -> MessageContentDto.Text(text)
    is Message.MessageContent.Image -> MessageContentDto.Image(url, thumbnailUrl, width, height)
    is Message.MessageContent.Video -> MessageContentDto.Video(url, thumbnailUrl, duration)
    is Message.MessageContent.File -> MessageContentDto.File(url, fileName, size, mimeType)
    is Message.MessageContent.Voice -> MessageContentDto.Voice(url, duration)
}
