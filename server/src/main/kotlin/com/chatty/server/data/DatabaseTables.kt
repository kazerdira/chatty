package com.chatty.server.data

import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.ReferenceOption

/**
 * Database schema using Exposed ORM
 */

object Users : UUIDTable("users") {
    val username = varchar("username", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val passwordHash = varchar("password_hash", 100)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val status = varchar("status", 50).default("offline") // online, offline, away
    val lastSeen = timestamp("last_seen").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object ChatRooms : UUIDTable("chat_rooms") {
    val name = varchar("name", 100)
    val type = varchar("type", 20) // direct, group, channel
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val createdBy = reference("created_by", Users, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object RoomParticipants : UUIDTable("room_participants") {
    val roomId = reference("room_id", ChatRooms, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 20).default("member") // owner, admin, member
    val joinedAt = timestamp("joined_at")
    val lastReadAt = timestamp("last_read_at").nullable()
    val unreadCount = integer("unread_count").default(0)
    
    init {
        uniqueIndex(roomId, userId)
    }
}

object Messages : UUIDTable("messages") {
    val roomId = reference("room_id", ChatRooms, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users, onDelete = ReferenceOption.CASCADE)
    val contentType = varchar("content_type", 20) // text, image, file, audio, video
    val contentText = text("content_text").nullable()
    val contentUrl = varchar("content_url", 500).nullable()
    val fileName = varchar("file_name", 255).nullable()
    val fileSize = long("file_size").nullable()
    val replyToId = reference("reply_to_id", Messages, onDelete = ReferenceOption.SET_NULL).nullable()
    val timestamp = timestamp("timestamp")
    val editedAt = timestamp("edited_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()
    
    init {
        index(false, roomId, timestamp) // For efficient message fetching
    }
}

object MessageStatus : UUIDTable("message_status") {
    val messageId = reference("message_id", Messages, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 20) // sent, delivered, read
    val timestamp = timestamp("timestamp")
    
    init {
        uniqueIndex(messageId, userId)
        index(false, messageId, status)
    }
}

object RefreshTokens : UUIDTable("refresh_tokens") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 500).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()
    
    init {
        index(false, userId, token)
    }
}

object TypingIndicators : UUIDTable("typing_indicators") {
    val roomId = reference("room_id", ChatRooms, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val startedAt = timestamp("started_at")
    
    init {
        uniqueIndex(roomId, userId)
    }
}
