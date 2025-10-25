package com.chatty.data.remote.dto

import com.chatty.domain.model.Message
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// ✅ FIXED: Server-compatible DTO (matches server's response exactly)
@Serializable
data class MessageDto(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    // ❌ REMOVED: senderAvatar (server doesn't return this field)
    val content: ServerMessageContentDto,
    val timestamp: String,
    val status: String,
    val editedAt: String? = null,
    val replyTo: String? = null
)

// Server's flat content structure (must match server exactly)
@Serializable
data class ServerMessageContentDto(
    val type: String,
    val text: String? = null,
    val url: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)

// Client's sealed class structure (used internally)
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

// ✅ ENHANCED: Mappers with better error handling
fun MessageDto.toEntity(): Message = Message(
    id = Message.MessageId(id),
    roomId = ChatRoom.RoomId(roomId),
    senderId = User.UserId(senderId),
    content = content.toEntity(),
    timestamp = Instant.parse(timestamp),
    status = try {
        Message.MessageStatus.valueOf(status.uppercase())
    } catch (e: Exception) {
        println("⚠️ Unknown message status '$status', defaulting to SENT")
        Message.MessageStatus.SENT
    },
    editedAt = editedAt?.let { Instant.parse(it) },
    replyTo = replyTo?.let { Message.MessageId(it) }
)

// Convert server's flat structure to domain entity
fun ServerMessageContentDto.toEntity(): Message.MessageContent = when (type.uppercase()) {
    "TEXT" -> Message.MessageContent.Text(text ?: "")
    "IMAGE" -> Message.MessageContent.Image(
        url = url ?: "",
        thumbnailUrl = url ?: "",
        width = null,
        height = null
    )
    "VIDEO" -> Message.MessageContent.Video(
        url = url ?: "",
        thumbnailUrl = url ?: "",
        duration = 0L
    )
    "FILE" -> Message.MessageContent.File(
        url = url ?: "",
        fileName = fileName ?: "file",
        size = fileSize ?: 0L,
        mimeType = "application/octet-stream"
    )
    "VOICE" -> Message.MessageContent.Voice(
        url = url ?: "",
        duration = 0L
    )
    else -> {
        println("⚠️ Unknown content type '$type', defaulting to TEXT")
        Message.MessageContent.Text(text ?: "")
    }
}

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

// ✅ CRITICAL: Convert client MessageContentDto to server's flat structure
fun MessageContentDto.toServerDto(): ServerMessageContentDto = when (this) {
    is MessageContentDto.Text -> ServerMessageContentDto(
        type = "TEXT",
        text = text,
        url = null,
        fileName = null,
        fileSize = null
    )
    is MessageContentDto.Image -> ServerMessageContentDto(
        type = "IMAGE",
        text = null,
        url = url,
        fileName = null,
        fileSize = null
    )
    is MessageContentDto.Video -> ServerMessageContentDto(
        type = "VIDEO",
        text = null,
        url = url,
        fileName = null,
        fileSize = null
    )
    is MessageContentDto.File -> ServerMessageContentDto(
        type = "FILE",
        text = null,
        url = url,
        fileName = fileName,
        fileSize = size
    )
    is MessageContentDto.Voice -> ServerMessageContentDto(
        type = "VOICE",
        text = null,
        url = url,
        fileName = null,
        fileSize = null
    )
}

// Request DTO for sending messages via HTTP API
@Serializable
data class SendMessageRequest(
    val roomId: String,
    val content: ServerMessageContentDto,
    val replyToId: String? = null
)
