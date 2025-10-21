package com.chatty.server

import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages WebSocket connections and message broadcasting
 * This is the core of real-time messaging functionality
 */
class WebSocketManager {
    // userId -> Set of WebSocket sessions (user can have multiple devices)
    private val userSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    
    // roomId -> Set of userIds currently in the room
    private val roomParticipants = ConcurrentHashMap<String, MutableSet<String>>()
    
    /**
     * Add a user's WebSocket connection
     */
    fun addConnection(userId: String, session: WebSocketSession) {
        userSessions.getOrPut(userId) { ConcurrentHashMap.newKeySet() }.add(session)
        println("🔌 WebSocket: User $userId connected (${userSessions[userId]?.size} sessions)")
    }
    
    /**
     * Remove a user's WebSocket connection
     */
    fun removeConnection(userId: String, session: WebSocketSession) {
        userSessions[userId]?.remove(session)
        if (userSessions[userId]?.isEmpty() == true) {
            userSessions.remove(userId)
            println("🔌 WebSocket: User $userId disconnected (all sessions closed)")
        }
    }
    
    /**
     * User joins a chat room
     */
    fun joinRoom(userId: String, roomId: String) {
        roomParticipants.getOrPut(roomId) { ConcurrentHashMap.newKeySet() }.add(userId)
        println("📍 WebSocket: User $userId joined room $roomId")
    }
    
    /**
     * User leaves a chat room
     */
    fun leaveRoom(userId: String, roomId: String) {
        roomParticipants[roomId]?.remove(userId)
        println("📍 WebSocket: User $userId left room $roomId")
    }
    
    /**
     * Send message to a specific user (all their devices)
     */
    suspend fun sendToUser(userId: String, message: WebSocketMessage) {
        val sessions = userSessions[userId] ?: return
        val json = Json.encodeToString(message)
        
        sessions.forEach { session ->
            try {
                session.send(Frame.Text(json))
            } catch (e: Exception) {
                println("❌ Failed to send to user $userId: ${e.message}")
                removeConnection(userId, session)
            }
        }
    }
    
    /**
     * Broadcast message to all users in a room (except sender)
     */
    suspend fun broadcastToRoom(roomId: String, message: WebSocketMessage, excludeUserId: String? = null) {
        val participants = roomParticipants[roomId] ?: emptySet()
        println("📢 Broadcasting to room $roomId: ${participants.size} participants")
        
        participants.forEach { userId ->
            if (userId != excludeUserId) {
                sendToUser(userId, message)
            }
        }
    }
    
    /**
     * Get online users in a room
     */
    fun getOnlineUsersInRoom(roomId: String): Set<String> {
        return roomParticipants[roomId]?.filter { userSessions.containsKey(it) }?.toSet() ?: emptySet()
    }
    
    /**
     * Check if user is online
     */
    fun isUserOnline(userId: String): Boolean {
        return userSessions.containsKey(userId)
    }
}

// ========================
// WebSocket Message Types
// ========================

@Serializable
sealed class WebSocketMessage {
    @Serializable
    data class Connected(
        val userId: String,
        val timestamp: String
    ) : WebSocketMessage()
    
    @Serializable
    data class NewMessage(
        val message: MessageDto
    ) : WebSocketMessage()
    
    @Serializable
    data class TypingIndicator(
        val roomId: String,
        val userId: String,
        val username: String,
        val isTyping: Boolean
    ) : WebSocketMessage()
    
    @Serializable
    data class MessageStatusUpdate(
        val messageId: String,
        val status: String
    ) : WebSocketMessage()
    
    @Serializable
    data class UserStatusUpdate(
        val userId: String,
        val status: String
    ) : WebSocketMessage()
    
    @Serializable
    data class Error(
        val message: String
    ) : WebSocketMessage()
}
