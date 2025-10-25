package com.chatty.server.data.repository

import com.chatty.server.ChatRoomDto
import com.chatty.server.data.DatabaseFactory.dbQuery
import com.chatty.server.data.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

class RoomRepository(
    private val messageRepository: MessageRepository
) {
    
    suspend fun createRoom(
        name: String,
        type: String,
        creatorId: String,
        participantIds: List<String>
    ): ChatRoomDto = dbQuery {
        val roomId = ChatRooms.insert {
            it[ChatRooms.name] = name
            it[ChatRooms.type] = type
            it[createdBy] = UUID.fromString(creatorId)
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } get ChatRooms.id
        
        // Add creator as admin
        RoomParticipants.insert {
            it[this.roomId] = roomId
            it[userId] = UUID.fromString(creatorId)
            it[role] = "ADMIN"
            it[joinedAt] = Instant.now()
        }
        
        // Add other participants
        participantIds.forEach { participantId ->
            if (participantId != creatorId) {
                RoomParticipants.insert {
                    it[this.roomId] = roomId
                    it[userId] = UUID.fromString(participantId)
                    it[role] = "MEMBER"
                    it[joinedAt] = Instant.now()
                }
            }
        }
        
        // Get the created room directly (within same transaction)
        println("DEBUG: Getting room by ID: ${roomId.value}")
        val room = ChatRooms.select { ChatRooms.id eq roomId }
            .firstOrNull()
        
        if (room == null) {
            println("ERROR: Failed to retrieve newly created room with ID: ${roomId.value}")
            throw RuntimeException("Failed to create room")
        }
        
        println("DEBUG: Room found, getting participants...")
        val participants = RoomParticipants
            .innerJoin(Users)
            .select { RoomParticipants.roomId eq roomId }
            .map { it[Users.id].value.toString() }
        
        println("DEBUG: Found ${participants.size} participants")
        
        println("DEBUG: Getting last message...")
        val lastMessage = Messages.join(Users, JoinType.INNER, Messages.senderId, Users.id)
            .select { Messages.roomId eq roomId }
            .orderBy(Messages.timestamp to SortOrder.DESC)
            .limit(1)
            .map { toSimpleMessageDto(it) }
            .firstOrNull()
        
        println("DEBUG: Creating ChatRoomDto...")
        ChatRoomDto(
            id = roomId.value.toString(),
            name = room[ChatRooms.name],
            type = room[ChatRooms.type],
            participants = participants,
            lastMessage = lastMessage,
            unreadCount = 0,
            createdAt = room[ChatRooms.createdAt].toString(),
            updatedAt = room[ChatRooms.updatedAt].toString()
        )
    }
    
    suspend fun getRoomById(roomId: String): ChatRoomDto? = dbQuery {
        println("DEBUG: Getting room by ID: $roomId")
        val room = ChatRooms.select { ChatRooms.id eq UUID.fromString(roomId) }
            .firstOrNull()
        
        if (room == null) {
            println("DEBUG: Room not found in ChatRooms table")
            return@dbQuery null
        }
        
        println("DEBUG: Room found, getting participants...")
        val participants = RoomParticipants
            .innerJoin(Users)
            .select { RoomParticipants.roomId eq UUID.fromString(roomId) }
            .map { it[Users.id].value.toString() }
        
        println("DEBUG: Found ${participants.size} participants")
        
        println("DEBUG: Getting last message...")
        val lastMessage = Messages.join(Users, JoinType.INNER, Messages.senderId, Users.id)
            .select { Messages.roomId eq UUID.fromString(roomId) }
            .orderBy(Messages.timestamp to SortOrder.DESC)
            .limit(1)
            .map { toSimpleMessageDto(it) }
            .firstOrNull()
        
        println("DEBUG: Creating ChatRoomDto...")
        val chatRoomDto = ChatRoomDto(
            id = roomId,
            name = room[ChatRooms.name],
            type = room[ChatRooms.type],
            participants = participants,
            lastMessage = lastMessage,
            unreadCount = 0, // Will be calculated per user
            createdAt = room[ChatRooms.createdAt].toString(),
            updatedAt = room[ChatRooms.updatedAt].toString(),
            avatarUrl = room[ChatRooms.avatarUrl]
        )
        
        println("DEBUG: ChatRoomDto created successfully")
        chatRoomDto
    }
    
    suspend fun getRoomsForUser(userId: String): List<ChatRoomDto> = dbQuery {
        val roomIds = RoomParticipants
            .select { RoomParticipants.userId eq UUID.fromString(userId) }
            .map { it[RoomParticipants.roomId].value.toString() }
        
        roomIds.mapNotNull { roomId ->
            val room = ChatRooms.select { ChatRooms.id eq UUID.fromString(roomId) }
                .firstOrNull() ?: return@mapNotNull null
            
            val participants = RoomParticipants
                .innerJoin(Users)
                .select { RoomParticipants.roomId eq UUID.fromString(roomId) }
                .map { it[Users.id].value.toString() }
            
            val lastMessage = Messages.join(Users, JoinType.INNER, Messages.senderId, Users.id)
                .select { Messages.roomId eq UUID.fromString(roomId) }
                .orderBy(Messages.timestamp to SortOrder.DESC)
                .limit(1)
                .map { toSimpleMessageDto(it) }
                .firstOrNull()
            
            val unreadCount = RoomParticipants
                .select {
                    (RoomParticipants.roomId eq UUID.fromString(roomId)) and
                    (RoomParticipants.userId eq UUID.fromString(userId))
                }
                .map { it[RoomParticipants.unreadCount] }
                .firstOrNull() ?: 0
            
            ChatRoomDto(
                id = roomId,
                name = room[ChatRooms.name],
                type = room[ChatRooms.type],
                participants = participants,
                lastMessage = lastMessage,
                unreadCount = unreadCount,
                createdAt = room[ChatRooms.createdAt].toString(),
                updatedAt = room[ChatRooms.updatedAt].toString(),
                avatarUrl = room[ChatRooms.avatarUrl]
            )
        }.sortedByDescending { it.updatedAt }
    }
    
    suspend fun addUserToRoom(roomId: String, userId: String) = dbQuery {
        RoomParticipants.insert {
            it[this.roomId] = UUID.fromString(roomId)
            it[this.userId] = UUID.fromString(userId)
            it[role] = "MEMBER"
            it[joinedAt] = Instant.now()
        }
        
        ChatRooms.update({ ChatRooms.id eq UUID.fromString(roomId) }) {
            it[updatedAt] = Instant.now()
        }
    }
    
    suspend fun removeUserFromRoom(roomId: String, userId: String) = dbQuery {
        RoomParticipants.deleteWhere {
            (RoomParticipants.roomId eq UUID.fromString(roomId)) and
                    (RoomParticipants.userId eq UUID.fromString(userId))
        }
        
        ChatRooms.update({ ChatRooms.id eq UUID.fromString(roomId) }) {
            it[updatedAt] = Instant.now()
        }
    }
    
    suspend fun userHasAccessToRoom(userId: String, roomId: String): Boolean = dbQuery {
        RoomParticipants.select {
            (RoomParticipants.roomId eq UUID.fromString(roomId)) and
            (RoomParticipants.userId eq UUID.fromString(userId))
        }.firstOrNull() != null
    }
    
    suspend fun markRoomAsRead(userId: String, roomId: String) = dbQuery {
        RoomParticipants.update({
            (RoomParticipants.userId eq UUID.fromString(userId)) and
            (RoomParticipants.roomId eq UUID.fromString(roomId))
        }) {
            it[unreadCount] = 0
        }
    }
    
    private fun toSimpleMessageDto(row: ResultRow): com.chatty.server.MessageDto {
        return com.chatty.server.MessageDto(
            id = row[Messages.id].value.toString(),
            roomId = row[Messages.roomId].value.toString(),
            senderId = row[Messages.senderId].value.toString(),
            senderName = row[Users.displayName],
            content = com.chatty.server.MessageContentDto(
                type = row[Messages.contentType],
                text = row[Messages.contentText]
            ),
            timestamp = row[Messages.timestamp].toString(),
            status = "sent",
            editedAt = row[Messages.editedAt]?.toString(),
            replyTo = row[Messages.replyToId]?.value?.toString()
        )
    }
}
