package com.chatty.data.messaging

import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.dto.ClientWebSocketMessage
import com.chatty.data.remote.dto.toDto
import com.chatty.domain.model.OutboxMessage
import com.chatty.domain.model.OutboxStatus
import com.chatty.domain.repository.OutboxRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Background processor that automatically sends pending messages from the outbox
 * Implements retry logic with exponential backoff
 * 
 * Key features:
 * - Automatic background processing every 5 seconds
 * - Exponential backoff for retries (1s, 2s, 4s, 8s, 16s, max 32s)
 * - Abandons messages after 5 failed attempts
 * - Real-time metrics for monitoring
 * - Manual retry capability
 */
class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val apiClient: ChatApiClient,
    private val scope: CoroutineScope,
    private val config: Config = Config()
) {
    /**
     * Configuration for the outbox processor
     */
    data class Config(
        val processingInterval: kotlin.time.Duration = 5.seconds,
        val maxRetries: Int = 5,
        val enableAutoProcessing: Boolean = true
    )
    
    private var processingJob: Job? = null
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _metrics = MutableStateFlow(Metrics())
    val metrics: StateFlow<Metrics> = _metrics.asStateFlow()
    
    /**
     * Metrics about outbox processing
     */
    data class Metrics(
        val pendingCount: Int = 0,
        val sendingCount: Int = 0,
        val failedCount: Int = 0,
        val abandonedCount: Int = 0,
        val totalProcessed: Int = 0,
        val lastProcessedAt: kotlinx.datetime.Instant? = null
    )
    
    /**
     * Start automatic background processing
     */
    fun start() {
        if (processingJob?.isActive == true) {
            println("⚠️ OutboxProcessor: Already running")
            return
        }
        
        println("✅ OutboxProcessor: Starting background processing (interval=${config.processingInterval})")
        
        processingJob = scope.launch {
            while (isActive && config.enableAutoProcessing) {
                try {
                    processPendingMessages()
                    updateMetrics()
                } catch (e: Exception) {
                    println("❌ OutboxProcessor: Error during processing - ${e.message}")
                    e.printStackTrace()
                }
                delay(config.processingInterval)
            }
        }
    }
    
    /**
     * Stop background processing
     */
    fun stop() {
        println("🛑 OutboxProcessor: Stopping background processing")
        processingJob?.cancel()
        processingJob = null
    }
    
    /**
     * Process all pending messages in the outbox
     */
    suspend fun processPendingMessages() {
        if (_isProcessing.value) {
            println("⏭️ OutboxProcessor: Already processing, skipping this cycle")
            return
        }
        
        _isProcessing.value = true
        
        try {
            val pending = outboxRepository.getPendingMessages()
            
            if (pending.isNotEmpty()) {
                println("📤 OutboxProcessor: Processing ${pending.size} pending messages")
            }
            
            pending.forEach { message ->
                processSingleMessage(message)
            }
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Process a single message by ID
     * 
     * @param messageId The message ID to process
     */
    suspend fun processSingleMessage(messageId: String) {
        val message = outboxRepository.getMessage(messageId) ?: run {
            println("⚠️ OutboxProcessor: Message $messageId not found in outbox")
            return
        }
        
        processSingleMessage(message)
    }
    
    private suspend fun processSingleMessage(message: OutboxMessage) {
        // Check if message should be retried
        if (!message.shouldRetry(config.maxRetries)) {
            if (message.status != OutboxStatus.ABANDONED) {
                println("🚫 OutboxProcessor: Message ${message.id} exceeded retry limit (${message.retryCount} attempts)")
                outboxRepository.updateStatus(message.id, OutboxStatus.ABANDONED)
            }
            return
        }
        
        // Check backoff delay
        message.lastRetryAt?.let { lastRetry ->
            val nextRetryDelay = message.calculateNextRetryDelay().milliseconds
            val nextRetryTime = lastRetry + nextRetryDelay
            val now = Clock.System.now()
            
            if (now < nextRetryTime) {
                val delayRemaining = nextRetryTime - now
                println("⏰ OutboxProcessor: Message ${message.id} waiting ${delayRemaining.inWholeSeconds}s before retry")
                return
            }
        }
        
        // Mark as sending
        println("📤 OutboxProcessor: Sending message ${message.id} (attempt ${message.retryCount + 1}/${config.maxRetries})")
        outboxRepository.updateStatus(message.id, OutboxStatus.SENDING)
        
        // Attempt to send
        val result = sendMessage(message)
        
        result.fold(
            onSuccess = { serverId ->
                println("✅ OutboxProcessor: Message ${message.id} sent successfully")
                outboxRepository.markAsSent(message.id, serverId)
                _metrics.value = _metrics.value.copy(
                    totalProcessed = _metrics.value.totalProcessed + 1
                )
            },
            onFailure = { error ->
                println("❌ OutboxProcessor: Message ${message.id} failed - ${error.message}")
                outboxRepository.incrementRetry(
                    id = message.id,
                    retryCount = message.retryCount + 1,
                    lastRetryAt = Clock.System.now()
                )
            }
        )
    }
    
    private suspend fun sendMessage(message: OutboxMessage): Result<String> {
        return try {
            // Send via WebSocket
            val clientMessage = ClientWebSocketMessage.SendMessage(
                messageId = message.id,
                roomId = message.roomId.value,
                content = message.content.toDto()
            )
            
            apiClient.sendClientMessage(clientMessage)
                .map { message.id } // Return client ID for now
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun updateMetrics() {
        val stats = outboxRepository.getStatistics()
        _metrics.value = Metrics(
            pendingCount = stats.pendingCount,
            sendingCount = stats.sendingCount,
            failedCount = stats.failedCount,
            abandonedCount = stats.abandonedCount,
            totalProcessed = _metrics.value.totalProcessed,
            lastProcessedAt = Clock.System.now()
        )
    }
    
    /**
     * Force retry of a failed or abandoned message
     * Resets the retry count to 0
     * 
     * @param messageId The message ID to retry
     * @return Success or failure
     */
    suspend fun retryMessage(messageId: String): Result<Unit> {
        return runCatching {
            val message = outboxRepository.getMessage(messageId)
                ?: throw IllegalArgumentException("Message not found in outbox")
            
            println("🔄 OutboxProcessor: Forcing retry of message $messageId")
            
            // Reset retry count and status
            outboxRepository.incrementRetry(
                id = messageId,
                retryCount = 0,
                lastRetryAt = Clock.System.now()
            )
            outboxRepository.updateStatus(messageId, OutboxStatus.PENDING)
            
            // Process immediately
            processSingleMessage(messageId)
        }
    }
    
    /**
     * Retry all failed messages
     * 
     * @return Number of messages reset for retry
     */
    suspend fun retryAllFailed(): Int {
        val pending = outboxRepository.getPendingMessages()
        val failed = pending.filter { it.status == OutboxStatus.FAILED || it.status == OutboxStatus.ABANDONED }
        
        println("🔄 OutboxProcessor: Retrying ${failed.size} failed messages")
        
        failed.forEach { message ->
            outboxRepository.incrementRetry(
                id = message.id,
                retryCount = 0,
                lastRetryAt = Clock.System.now()
            )
            outboxRepository.updateStatus(message.id, OutboxStatus.PENDING)
        }
        
        return failed.size
    }
}
