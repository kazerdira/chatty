package com.chatty.domain.repository

import com.chatty.domain.model.OutboxMessage
import com.chatty.domain.model.OutboxStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing the message outbox
 * Part of the Outbox Pattern for reliable message delivery
 */
interface OutboxRepository {
    
    /**
     * Insert a new message into the outbox
     * 
     * @param message The message to add to the outbox
     */
    suspend fun insertMessage(message: OutboxMessage)
    
    /**
     * Get a specific message from the outbox
     * 
     * @param id The message ID
     * @return The outbox message, or null if not found
     */
    suspend fun getMessage(id: String): OutboxMessage?
    
    /**
     * Get all pending messages that need to be sent
     * 
     * @return List of messages with PENDING or FAILED status, ordered by creation time
     */
    suspend fun getPendingMessages(): List<OutboxMessage>
    
    /**
     * Get all outbox messages for a specific room
     * 
     * @param roomId The room ID
     * @return List of outbox messages in the room
     */
    suspend fun getMessagesByRoom(roomId: String): List<OutboxMessage>
    
    /**
     * Update the status of a message
     * 
     * @param id The message ID
     * @param status The new status
     */
    suspend fun updateStatus(id: String, status: OutboxStatus)
    
    /**
     * Increment retry count and update last retry timestamp
     * 
     * @param id The message ID
     * @param retryCount The new retry count
     * @param lastRetryAt Timestamp of this retry attempt
     */
    suspend fun incrementRetry(id: String, retryCount: Int, lastRetryAt: kotlinx.datetime.Instant)
    
    /**
     * Mark a message as successfully sent and remove from outbox
     * 
     * @param id The message ID
     * @param serverId The server-assigned ID (optional)
     */
    suspend fun markAsSent(id: String, serverId: String? = null)
    
    /**
     * Delete a message from the outbox
     * 
     * @param id The message ID
     */
    suspend fun deleteMessage(id: String)
    
    /**
     * Delete all outbox messages for a specific room
     * 
     * @param roomId The room ID
     */
    suspend fun deleteMessagesByRoom(roomId: String)
    
    /**
     * Get statistics about the outbox
     * 
     * @return Statistics object with counts by status
     */
    suspend fun getStatistics(): OutboxStatistics
    
    /**
     * Observe outbox messages for a specific room
     * 
     * @param roomId The room ID
     * @return Flow of outbox messages
     */
    fun observeMessagesByRoom(roomId: String): Flow<List<OutboxMessage>>
}

/**
 * Statistics about the message outbox
 */
data class OutboxStatistics(
    val pendingCount: Int = 0,
    val sendingCount: Int = 0,
    val failedCount: Int = 0,
    val abandonedCount: Int = 0
)
