package com.chatty.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a message waiting to be sent to the server
 * Part of the Outbox Pattern for reliable message delivery
 * 
 * @property id Client-generated unique ID (UUID)
 * @property roomId Room where the message should be sent
 * @property senderId User who is sending the message
 * @property content Message content (text, image, file, etc.)
 * @property timestamp When the message was created
 * @property status Current status in the outbox (PENDING, SENDING, FAILED, ABANDONED)
 * @property retryCount Number of send attempts made
 * @property lastRetryAt Timestamp of the last retry attempt
 * @property createdAt When the message was first added to outbox
 */
@Serializable
data class OutboxMessage(
    val id: String,
    val roomId: ChatRoom.RoomId,
    val senderId: User.UserId,
    val content: Message.MessageContent,
    val timestamp: Instant,
    val status: OutboxStatus,
    val retryCount: Int = 0,
    val lastRetryAt: Instant? = null,
    val createdAt: Instant
) {
    /**
     * Convert outbox message to a regular Message for UI display
     */
    fun toMessage(): Message {
        return Message(
            id = Message.MessageId(id),
            roomId = roomId,
            senderId = senderId,
            content = content,
            timestamp = timestamp,
            status = when (status) {
                OutboxStatus.PENDING -> Message.MessageStatus.SENDING
                OutboxStatus.SENDING -> Message.MessageStatus.SENDING
                OutboxStatus.FAILED -> Message.MessageStatus.FAILED
                OutboxStatus.ABANDONED -> Message.MessageStatus.FAILED
            },
            editedAt = null,
            replyTo = null
        )
    }
    
    /**
     * Check if this message should be retried
     * 
     * @param maxRetries Maximum number of retry attempts allowed
     * @return true if message should be retried, false if it should be abandoned
     */
    fun shouldRetry(maxRetries: Int = 5): Boolean {
        return retryCount < maxRetries && status != OutboxStatus.ABANDONED
    }
    
    /**
     * Calculate the delay before next retry using exponential backoff
     * 
     * Backoff schedule:
     * - Attempt 1: 1 second
     * - Attempt 2: 2 seconds
     * - Attempt 3: 4 seconds
     * - Attempt 4: 8 seconds
     * - Attempt 5: 16 seconds
     * - Maximum: 32 seconds
     * 
     * @return Delay in milliseconds
     */
    fun calculateNextRetryDelay(): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 32s
        val baseDelay = 1000L
        val exponentialDelay = baseDelay * (1 shl retryCount) // 2^retryCount
        return minOf(exponentialDelay, 32000L)
    }
}

/**
 * Status of a message in the outbox
 */
@Serializable
enum class OutboxStatus {
    /**
     * Message is ready to be sent
     */
    PENDING,
    
    /**
     * Message is currently being sent
     */
    SENDING,
    
    /**
     * Message send failed, will be retried
     */
    FAILED,
    
    /**
     * Message has failed too many times, manual intervention needed
     */
    ABANDONED
}
