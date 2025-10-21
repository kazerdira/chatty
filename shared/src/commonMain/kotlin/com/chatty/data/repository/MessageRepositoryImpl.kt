package com.chatty.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.chatty.data.local.TokenManager
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.dto.*
import com.chatty.database.ChatDatabase
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import com.chatty.domain.model.User
import com.chatty.domain.repository.MessageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class MessageRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val database: ChatDatabase,
    private val tokenManager: TokenManager,
    private val scope: CoroutineScope
) : MessageRepository {
    
    init {
        // Listen to WebSocket messages
        scope.launch {
            apiClient.incomingMessages.collect { wsMessage ->
                when (wsMessage) {
                    is WebSocketMessage.MessageReceived -> {
                        handleMessageReceived(wsMessage.message)
                    }
                    is WebSocketMessage.MessageStatusUpdate -> {
                        database.chatDatabaseQueries.updateMessageStatus(
                            wsMessage.status.name,
                            wsMessage.messageId
                        )
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun handleMessageReceived(messageDto: MessageDto) {
        database.chatDatabaseQueries.insertMessage(
            id = messageDto.id,
            roomId = messageDto.roomId,
            senderId = messageDto.senderId,
            senderName = messageDto.senderName,
            senderAvatar = messageDto.senderAvatar,
            contentType = getContentType(messageDto.content),
            contentData = serializeContent(messageDto.content),
            timestamp = messageDto.timestamp.toEpochMilliseconds(),
            status = messageDto.status.name,
            editedAt = messageDto.editedAt?.toEpochMilliseconds(),
            replyToId = messageDto.replyTo
        )
        
        // Update room's updated timestamp
        database.chatDatabaseQueries.updateRoomUpdatedAt(
            messageDto.timestamp.toEpochMilliseconds(),
            messageDto.roomId
        )
    }
    
    override suspend fun sendMessage(message: Message): Result<Message> {
        return runCatching {
            // Save to local DB first
            database.chatDatabaseQueries.insertMessage(
                id = message.id.value,
                roomId = message.roomId.value,
                senderId = message.senderId.value,
                senderName = "", // Will be populated from current user
                senderAvatar = null,
                contentType = getContentType(message.content),
                contentData = serializeContent(message.content.toDto()),
                timestamp = message.timestamp.toEpochMilliseconds(),
                status = Message.MessageStatus.SENDING.name,
                editedAt = message.editedAt?.toEpochMilliseconds(),
                replyToId = message.replyTo?.value
            )
            
            // Send via WebSocket
            apiClient.sendMessage(
                WebSocketMessage.SendMessage(
                    roomId = message.roomId.value,
                    content = message.content.toDto(),
                    replyToId = message.replyTo?.value,
                    tempId = message.id.value
                )
            ).fold(
                onSuccess = { message },
                onFailure = { error ->
                    // Update status to FAILED
                    database.chatDatabaseQueries.updateMessageStatus(
                        Message.MessageStatus.FAILED.name,
                        message.id.value
                    )
                    throw error
                }
            )
        }
    }
    
    override suspend fun getMessage(messageId: Message.MessageId): Message? {
        val localMessage = database.chatDatabaseQueries
            .selectMessageById(messageId.value)
            .executeAsOneOrNull()
        
        return localMessage?.let { dbMessage ->
            Message(
                id = Message.MessageId(dbMessage.id),
                roomId = ChatRoom.RoomId(dbMessage.roomId),
                senderId = User.UserId(dbMessage.senderId),
                content = deserializeContent(dbMessage.contentType, dbMessage.contentData),
                timestamp = Instant.fromEpochMilliseconds(dbMessage.timestamp),
                status = Message.MessageStatus.valueOf(dbMessage.status),
                editedAt = dbMessage.editedAt?.let { Instant.fromEpochMilliseconds(it) },
                replyTo = dbMessage.replyToId?.let { Message.MessageId(it) }
            )
        }
    }
    
    override suspend fun getMessages(
        roomId: ChatRoom.RoomId,
        before: Message.MessageId?,
        limit: Int
    ): Result<List<Message>> {
        return runCatching {
            // Try local first
            val localMessages = if (before != null) {
                val beforeMessage = getMessage(before)
                database.chatDatabaseQueries.selectMessagesBefore(
                    roomId.value,
                    beforeMessage?.timestamp?.toEpochMilliseconds() ?: 0L,
                    limit.toLong()
                ).executeAsList()
            } else {
                database.chatDatabaseQueries.selectMessages(
                    roomId.value,
                    limit.toLong()
                ).executeAsList()
            }
            
            if (localMessages.isEmpty()) {
                // Fetch from server
                apiClient.getMessages(roomId.value, before?.value, limit).fold(
                    onSuccess = { dtos ->
                        dtos.forEach { handleMessageReceived(it) }
                        dtos.map { it.toEntity() }
                    },
                    onFailure = { emptyList() }
                )
            } else {
                localMessages.map { dbMessage ->
                    Message(
                        id = Message.MessageId(dbMessage.id),
                        roomId = ChatRoom.RoomId(dbMessage.roomId),
                        senderId = User.UserId(dbMessage.senderId),
                        content = deserializeContent(dbMessage.contentType, dbMessage.contentData),
                        timestamp = Instant.fromEpochMilliseconds(dbMessage.timestamp),
                        status = Message.MessageStatus.valueOf(dbMessage.status),
                        editedAt = dbMessage.editedAt?.let { Instant.fromEpochMilliseconds(it) },
                        replyTo = dbMessage.replyToId?.let { Message.MessageId(it) }
                    )
                }
            }
        }
    }
    
    override fun observeMessages(roomId: ChatRoom.RoomId): Flow<List<Message>> {
        return database.chatDatabaseQueries
            .selectMessages(roomId.value, 1000)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { dbMessages ->
                dbMessages.map { dbMessage ->
                    Message(
                        id = Message.MessageId(dbMessage.id),
                        roomId = ChatRoom.RoomId(dbMessage.roomId),
                        senderId = User.UserId(dbMessage.senderId),
                        content = deserializeContent(dbMessage.contentType, dbMessage.contentData),
                        timestamp = Instant.fromEpochMilliseconds(dbMessage.timestamp),
                        status = Message.MessageStatus.valueOf(dbMessage.status),
                        editedAt = dbMessage.editedAt?.let { Instant.fromEpochMilliseconds(it) },
                        replyTo = dbMessage.replyToId?.let { Message.MessageId(it) }
                    )
                }
            }
    }
    
    override suspend fun markAsRead(messageIds: List<Message.MessageId>): Result<Unit> {
        return runCatching {
            messageIds.forEach { messageId ->
                database.chatDatabaseQueries.updateMessageStatus(
                    Message.MessageStatus.READ.name,
                    messageId.value
                )
            }
            
            apiClient.sendMessage(
                WebSocketMessage.MarkAsRead(messageIds.map { it.value })
            ).getOrThrow()
        }
    }
    
    override suspend fun deleteMessage(messageId: Message.MessageId): Result<Unit> {
        return runCatching {
            database.chatDatabaseQueries.deleteMessage(messageId.value)
        }
    }
    
    override suspend fun editMessage(
        messageId: Message.MessageId,
        newContent: Message.MessageContent
    ): Result<Message> {
        return runCatching {
            val now = kotlinx.datetime.Clock.System.now()
            database.chatDatabaseQueries.updateMessageContent(
                contentType = getContentType(newContent),
                contentData = serializeContent(newContent.toDto()),
                editedAt = now.toEpochMilliseconds(),
                id = messageId.value
            )
            
            getMessage(messageId) ?: throw IllegalStateException("Message not found")
        }
    }
    
    override suspend fun syncMessages(roomId: ChatRoom.RoomId): Result<Unit> {
        return runCatching {
            apiClient.getMessages(roomId.value).fold(
                onSuccess = { messages ->
                    messages.forEach { handleMessageReceived(it) }
                },
                onFailure = { throw it }
            )
        }
    }
    
    private fun getContentType(content: Message.MessageContent): String {
        return when (content) {
            is Message.MessageContent.Text -> "TEXT"
            is Message.MessageContent.Image -> "IMAGE"
            is Message.MessageContent.Video -> "VIDEO"
            is Message.MessageContent.File -> "FILE"
            is Message.MessageContent.Voice -> "VOICE"
        }
    }
    
    private fun getContentType(content: MessageContentDto): String {
        return when (content) {
            is MessageContentDto.Text -> "TEXT"
            is MessageContentDto.Image -> "IMAGE"
            is MessageContentDto.Video -> "VIDEO"
            is MessageContentDto.File -> "FILE"
            is MessageContentDto.Voice -> "VOICE"
        }
    }
    
    private fun serializeContent(content: MessageContentDto): String {
        return when (content) {
            is MessageContentDto.Text -> content.text
            is MessageContentDto.Image -> "${content.url}|${content.thumbnailUrl}|${content.width}|${content.height}"
            is MessageContentDto.Video -> "${content.url}|${content.thumbnailUrl}|${content.duration}"
            is MessageContentDto.File -> "${content.url}|${content.fileName}|${content.size}|${content.mimeType}"
            is MessageContentDto.Voice -> "${content.url}|${content.duration}"
        }
    }
    
    private fun deserializeContent(type: String, data: String): Message.MessageContent {
        return when (type) {
            "TEXT" -> Message.MessageContent.Text(data)
            "IMAGE" -> {
                val parts = data.split("|")
                Message.MessageContent.Image(
                    url = parts[0],
                    thumbnailUrl = parts[1],
                    width = parts.getOrNull(2)?.toIntOrNull(),
                    height = parts.getOrNull(3)?.toIntOrNull()
                )
            }
            "VIDEO" -> {
                val parts = data.split("|")
                Message.MessageContent.Video(
                    url = parts[0],
                    thumbnailUrl = parts[1],
                    duration = parts[2].toLong()
                )
            }
            "FILE" -> {
                val parts = data.split("|")
                Message.MessageContent.File(
                    url = parts[0],
                    fileName = parts[1],
                    size = parts[2].toLong(),
                    mimeType = parts[3]
                )
            }
            "VOICE" -> {
                val parts = data.split("|")
                Message.MessageContent.Voice(
                    url = parts[0],
                    duration = parts[1].toLong()
                )
            }
            else -> Message.MessageContent.Text(data)
        }
    }
}
