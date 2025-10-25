package com.chatty.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.chatty.database.ChatDatabase
import com.chatty.database.MessageOutbox
import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import com.chatty.domain.model.OutboxMessage
import com.chatty.domain.model.OutboxStatus
import com.chatty.domain.model.User
import com.chatty.domain.repository.OutboxRepository
import com.chatty.domain.repository.OutboxStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * SQLDelight implementation of OutboxRepository
 */
class OutboxRepositoryImpl(
    private val database: ChatDatabase
) : OutboxRepository {
    
    override suspend fun insertMessage(message: OutboxMessage) {
        database.chatDatabaseQueries.insertOutboxMessage(
            id = message.id,
            roomId = message.roomId.value,
            senderId = message.senderId.value,
            contentType = getContentType(message.content),
            contentData = serializeContent(message.content),
            timestamp = message.timestamp.toEpochMilliseconds(),
            status = message.status.name,
            retryCount = message.retryCount.toLong(),
            lastRetryAt = message.lastRetryAt?.toEpochMilliseconds(),
            createdAt = message.createdAt.toEpochMilliseconds()
        )
    }
    
    override suspend fun getMessage(id: String): OutboxMessage? {
        return database.chatDatabaseQueries
            .selectOutboxMessageById(id)
            .executeAsOneOrNull()
            ?.toDomainModel()
    }
    
    override suspend fun getPendingMessages(): List<OutboxMessage> {
        return database.chatDatabaseQueries
            .selectPendingOutboxMessages()
            .executeAsList()
            .map { it.toDomainModel() }
    }
    
    override suspend fun getMessagesByRoom(roomId: String): List<OutboxMessage> {
        return database.chatDatabaseQueries
            .selectOutboxMessagesByRoom(roomId)
            .executeAsList()
            .map { it.toDomainModel() }
    }
    
    override suspend fun updateStatus(id: String, status: OutboxStatus) {
        database.chatDatabaseQueries.updateOutboxStatus(
            status = status.name,
            id = id
        )
    }
    
    override suspend fun incrementRetry(
        id: String,
        retryCount: Int,
        lastRetryAt: kotlinx.datetime.Instant
    ) {
        database.chatDatabaseQueries.updateOutboxRetry(
            retryCount = retryCount.toLong(),
            lastRetryAt = lastRetryAt.toEpochMilliseconds(),
            id = id
        )
        
        // Update status to FAILED to indicate retry is needed
        database.chatDatabaseQueries.updateOutboxStatus(
            status = OutboxStatus.FAILED.name,
            id = id
        )
    }
    
    override suspend fun markAsSent(id: String, serverId: String?) {
        // Simply delete from outbox once sent successfully
        database.chatDatabaseQueries.deleteOutboxMessage(id)
    }
    
    override suspend fun deleteMessage(id: String) {
        database.chatDatabaseQueries.deleteOutboxMessage(id)
    }
    
    override suspend fun deleteMessagesByRoom(roomId: String) {
        database.chatDatabaseQueries.deleteOutboxMessagesByRoom(roomId)
    }
    
    override suspend fun getStatistics(): OutboxStatistics {
        val rows = database.chatDatabaseQueries
            .countOutboxByStatus()
            .executeAsList()
        
        val statusCounts = rows.associate { row -> 
            row.status to row.COUNT.toInt()
        }
        
        return OutboxStatistics(
            pendingCount = statusCounts[OutboxStatus.PENDING.name] ?: 0,
            sendingCount = statusCounts[OutboxStatus.SENDING.name] ?: 0,
            failedCount = statusCounts[OutboxStatus.FAILED.name] ?: 0,
            abandonedCount = statusCounts[OutboxStatus.ABANDONED.name] ?: 0
        )
    }
    
    override fun observeMessagesByRoom(roomId: String): Flow<List<OutboxMessage>> {
        return database.chatDatabaseQueries
            .selectOutboxMessagesByRoom(roomId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomainModel() } }
    }
    
    // Helper functions for content serialization
    
    private fun getContentType(content: Message.MessageContent): String {
        return when (content) {
            is Message.MessageContent.Text -> "TEXT"
            is Message.MessageContent.Image -> "IMAGE"
            is Message.MessageContent.File -> "FILE"
            is Message.MessageContent.Video -> "VIDEO"
            is Message.MessageContent.Voice -> "VOICE"
        }
    }
    
    private fun serializeContent(content: Message.MessageContent): String {
        return when (content) {
            is Message.MessageContent.Text -> content.text
            is Message.MessageContent.Image -> "${content.url}|${content.thumbnailUrl}|${content.width ?: ""}|${content.height ?: ""}"
            is Message.MessageContent.File -> "${content.url}|${content.fileName}|${content.size}|${content.mimeType}"
            is Message.MessageContent.Video -> "${content.url}|${content.thumbnailUrl}|${content.duration}"
            is Message.MessageContent.Voice -> "${content.url}|${content.duration}"
        }
    }
    
    private fun deserializeContent(contentType: String, contentData: String): Message.MessageContent {
        return when (contentType) {
            "TEXT" -> Message.MessageContent.Text(contentData)
            "IMAGE" -> {
                val parts = contentData.split("|")
                Message.MessageContent.Image(
                    url = parts[0],
                    thumbnailUrl = parts[1],
                    width = parts.getOrNull(2)?.toIntOrNull(),
                    height = parts.getOrNull(3)?.toIntOrNull()
                )
            }
            "FILE" -> {
                val parts = contentData.split("|")
                Message.MessageContent.File(
                    url = parts[0],
                    fileName = parts[1],
                    size = parts[2].toLong(),
                    mimeType = parts[3]
                )
            }
            "VIDEO" -> {
                val parts = contentData.split("|")
                Message.MessageContent.Video(
                    url = parts[0],
                    thumbnailUrl = parts[1],
                    duration = parts[2].toLong()
                )
            }
            "VOICE" -> {
                val parts = contentData.split("|")
                Message.MessageContent.Voice(
                    url = parts[0],
                    duration = parts[1].toLong()
                )
            }
            else -> Message.MessageContent.Text(contentData)
        }
    }
    
    // Extension function to convert database model to domain model
    
    private fun MessageOutbox.toDomainModel(): OutboxMessage {
        return OutboxMessage(
            id = id,
            roomId = ChatRoom.RoomId(roomId),
            senderId = User.UserId(senderId),
            content = deserializeContent(contentType, contentData),
            timestamp = Instant.fromEpochMilliseconds(timestamp),
            status = OutboxStatus.valueOf(status),
            retryCount = retryCount.toInt(),
            lastRetryAt = lastRetryAt?.let { Instant.fromEpochMilliseconds(it) },
            createdAt = Instant.fromEpochMilliseconds(createdAt)
        )
    }
}
