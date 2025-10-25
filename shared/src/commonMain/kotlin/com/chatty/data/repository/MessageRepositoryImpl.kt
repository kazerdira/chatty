package com.chatty.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.chatty.data.local.TokenManager
import com.chatty.data.messaging.OutboxProcessor
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.dto.*
import com.chatty.database.ChatDatabase
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import com.chatty.domain.model.OutboxMessage
import com.chatty.domain.model.OutboxStatus
import com.chatty.domain.model.User
import com.chatty.domain.repository.MessageRepository
import com.chatty.domain.repository.OutboxRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class MessageRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val database: ChatDatabase,
    private val tokenManager: TokenManager,
    private val outboxRepository: OutboxRepository,
    private val outboxProcessor: OutboxProcessor,
    private val scope: CoroutineScope
) : MessageRepository {
    
    init {
        println("🔧 MessageRepository: Initializing with Outbox Pattern (fix6)")
        
        // Start outbox processor for automatic retry
        outboxProcessor.start()
        
        // Listen to WebSocket messages
        scope.launch {
            apiClient.incomingMessages.collect { wsMessage ->
                when (wsMessage) {
                    is WebSocketMessage.NewMessage -> {
                        // Handle real-time messages from other users
                        println("📨 Received new message: ${wsMessage.message.id}")
                        handleMessageReceived(wsMessage.message)
                    }
                    is WebSocketMessage.MessageReceived -> {
                        handleMessageReceived(wsMessage.message)
                    }
                    is WebSocketMessage.MessageSent -> {
                        // Update temp message with real message from server
                        println("✅ Message sent successfully: ${wsMessage.message.id}")
                        database.chatDatabaseQueries.deleteMessage(wsMessage.tempId)
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
            contentType = messageDto.content.type, // Use server's flat structure
            contentData = messageDto.content.text ?: messageDto.content.url ?: "", // Simple serialization
            timestamp = Instant.parse(messageDto.timestamp).toEpochMilliseconds(),
            status = messageDto.status, // Already a string
            editedAt = messageDto.editedAt?.let { Instant.parse(it).toEpochMilliseconds() },
            replyToId = messageDto.replyTo
        )
        
        // Update room's updated timestamp
        database.chatDatabaseQueries.updateRoomUpdatedAt(
            Instant.parse(messageDto.timestamp).toEpochMilliseconds(),
            messageDto.roomId
        )
    }
    
    override suspend fun sendMessage(message: Message): Result<Message> {
        return runCatching {
            val clientId = message.id.value
            val now = Clock.System.now()
            
            println("📤 MessageRepository: Sending message $clientId with Outbox Pattern")
            
            // Create outbox message (GUARANTEED DELIVERY)
            val outboxMessage = OutboxMessage(
                id = clientId,
                roomId = message.roomId,
                senderId = message.senderId,
                content = message.content,
                timestamp = message.timestamp,
                status = OutboxStatus.PENDING,
                retryCount = 0,
                lastRetryAt = null,
                createdAt = now
            )
            
            // Save to outbox (guarantees delivery even if app crashes)
            outboxRepository.insertMessage(outboxMessage)
            println("💾 MessageRepository: Saved to outbox")
            
            // Save to messages table (optimistic update for instant UI)
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
            println("💾 MessageRepository: Saved to messages (optimistic)")
            
            // Trigger immediate send attempt (don't wait for background processor)
            scope.launch {
                outboxProcessor.processSingleMessage(clientId)
            }
            
            message
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
                // Return messages in chronological order (oldest first)
                localMessages.reversed().map { dbMessage ->
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
                // Reverse to show oldest first (chronological order for chat)
                dbMessages.reversed().map { dbMessage ->
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
