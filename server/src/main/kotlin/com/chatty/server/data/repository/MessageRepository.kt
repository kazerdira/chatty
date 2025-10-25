package com.chatty.server.data.repository

import com.chatty.server.MessageDto
import com.chatty.server.MessageContentDto
import com.chatty.server.data.DatabaseFactory.dbQuery
import com.chatty.server.data.tables.*
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.*

class MessageRepository {
    
    suspend fun sendMessage(
        roomId: String,
        senderId: String,
        contentType: String,
        contentText: String? = null,
        contentUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        replyToId: String? = null
    ): MessageDto? = dbQuery {
        println("🔍 [MessageRepository] Inserting message - roomId: $roomId, senderId: $senderId, contentType: $contentType")
        
        val messageId = Messages.insert {
            it[Messages.roomId] = UUID.fromString(roomId)
            it[Messages.senderId] = UUID.fromString(senderId)
            it[Messages.contentType] = contentType
            it[Messages.contentText] = contentText
            it[Messages.contentUrl] = contentUrl
            it[Messages.fileName] = fileName
            it[Messages.fileSize] = fileSize
            it[Messages.replyToId] = replyToId?.let { id -> UUID.fromString(id) }
            it[timestamp] = Instant.now()
            it[status] = "SENT"
        } get Messages.id
        
        println("✅ [MessageRepository] Message inserted with ID: ${messageId.value}")
        
        // Mark as sent for sender
        MessageStatus.insert {
            it[this.messageId] = messageId
            it[userId] = UUID.fromString(senderId)
            it[status] = "sent"
            it[MessageStatus.timestamp] = Instant.now()
        }
        
        // Update room updated_at
        ChatRooms.update({ ChatRooms.id eq UUID.fromString(roomId) }) {
            it[updatedAt] = Instant.now()
        }
        
        // Increment unread count for other participants
        RoomParticipants.update({
            (RoomParticipants.roomId eq UUID.fromString(roomId)) and
            (RoomParticipants.userId neq UUID.fromString(senderId))
        }) {
            with(SqlExpressionBuilder) {
                it[unreadCount] = unreadCount + 1
            }
        }
        
        getMessageById(messageId.value.toString())
    }
    
    suspend fun getMessageById(messageId: String): MessageDto? = dbQuery {
        Messages.join(Users, JoinType.INNER, Messages.senderId, Users.id)
            .select { Messages.id eq UUID.fromString(messageId) }
            .map { toMessageDto(it) }
            .firstOrNull()
    }
    
    suspend fun getMessages(roomId: String, limit: Int = 50, offset: Int = 0): List<MessageDto> = 
        dbQuery {
            Messages.join(Users, JoinType.INNER, Messages.senderId, Users.id)
                .select { 
                    (Messages.roomId eq UUID.fromString(roomId)) and
                    Messages.deletedAt.isNull()
                }
                .orderBy(Messages.timestamp to SortOrder.ASC)
                .limit(limit, offset.toLong())
                .map { toMessageDto(it) }
        }
    
    suspend fun getLastMessageForRoom(roomId: UUID): MessageDto? = dbQuery {
        Messages.join(Users, JoinType.INNER, Messages.senderId, Users.id)
            .select { Messages.roomId eq roomId }
            .orderBy(Messages.timestamp to SortOrder.DESC)
            .limit(1)
            .map { toMessageDto(it) }
            .firstOrNull()
    }
    
    suspend fun updateMessageStatus(messageId: String, userId: String, status: String) = 
        dbQuery {
            MessageStatus.insertIgnore {
                it[this.messageId] = UUID.fromString(messageId)
                it[this.userId] = UUID.fromString(userId)
                it[this.status] = status
                it[timestamp] = Instant.now()
            }
        }
    
    suspend fun editMessage(messageId: String, newContent: String): MessageDto? = dbQuery {
        Messages.update({ Messages.id eq UUID.fromString(messageId) }) {
            it[contentText] = newContent
            it[editedAt] = Instant.now()
        }
        
        getMessageById(messageId)
    }
    
    suspend fun deleteMessage(messageId: String): Boolean = dbQuery {
        val updated = Messages.update({ Messages.id eq UUID.fromString(messageId) }) {
            it[deletedAt] = Instant.now()
        }
        updated > 0
    }
    
    private fun toMessageDto(row: ResultRow): MessageDto {
        return MessageDto(
            id = row[Messages.id].value.toString(),
            roomId = row[Messages.roomId].value.toString(),
            senderId = row[Messages.senderId].value.toString(),
            senderName = row[Users.displayName],
            content = MessageContentDto(
                type = row[Messages.contentType],
                text = row[Messages.contentText],
                url = row[Messages.contentUrl],
                fileName = row[Messages.fileName],
                fileSize = row[Messages.fileSize]
            ),
            timestamp = row[Messages.timestamp].toString(),
            status = row[Messages.status],
            editedAt = row[Messages.editedAt]?.toString(),
            replyTo = row[Messages.replyToId]?.value?.toString()
        )
    }
}
