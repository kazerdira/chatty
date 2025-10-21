package com.chatty.server.data.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.*
import java.time.Instant

object Users : UUIDTable("users") {
    val username = varchar("username", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val avatarUrl = text("avatar_url").nullable()
    val passwordHash = varchar("password_hash", 100)
    val status = varchar("status", 20).default("OFFLINE")
    val lastSeen = timestamp("last_seen").defaultExpression(CurrentTimestamp())
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

object ChatRooms : UUIDTable("chat_rooms") {
    val name = varchar("name", 100)
    val type = varchar("type", 20) // DIRECT, GROUP, CHANNEL
    val avatarUrl = text("avatar_url").nullable()
    val createdBy = reference("created_by", Users)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

object RoomParticipants : Table("room_participants") {
    val roomId = reference("room_id", ChatRooms)
    val userId = reference("user_id", Users)
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp())
    val role = varchar("role", 20).default("MEMBER") // ADMIN, MODERATOR, MEMBER
    val unreadCount = integer("unread_count").default(0)
    
    override val primaryKey = PrimaryKey(roomId, userId)
}

object Messages : UUIDTable("messages") {
    val roomId = reference("room_id", ChatRooms)
    val senderId = reference("sender_id", Users)
    val contentType = varchar("content_type", 20) // TEXT, IMAGE, VIDEO, FILE, VOICE
    val contentText = text("content_text").nullable()
    val contentUrl = text("content_url").nullable()
    val fileName = varchar("file_name", 255).nullable()
    val fileSize = long("file_size").nullable()
    val timestamp: org.jetbrains.exposed.sql.Column<Instant> = timestamp("timestamp").defaultExpression(CurrentTimestamp())
    val status = varchar("status", 20).default("SENT")
    val editedAt: org.jetbrains.exposed.sql.Column<Instant?> = timestamp("edited_at").nullable()
    val replyToId = reference("reply_to_id", Messages).nullable()
    val deletedAt: org.jetbrains.exposed.sql.Column<Instant?> = timestamp("deleted_at").nullable()
}

object MessageStatus : Table("message_status") {
    val messageId = reference("message_id", Messages)
    val userId = reference("user_id", Users)
    val status = varchar("status", 20) // DELIVERED, READ
    val timestamp: org.jetbrains.exposed.sql.Column<Instant> = timestamp("timestamp").defaultExpression(CurrentTimestamp())
    
    override val primaryKey = PrimaryKey(messageId, userId)
}

object RefreshTokens : Table("refresh_tokens") {
    val userId = reference("user_id", Users)
    val token = varchar("token", 100).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    
    override val primaryKey = PrimaryKey(userId, token)
}

object TypingIndicators : Table("typing_indicators") {
    val roomId = reference("room_id", ChatRooms)
    val userId = reference("user_id", Users)
    val isTyping = bool("is_typing")
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
    
    override val primaryKey = PrimaryKey(roomId, userId)
}
