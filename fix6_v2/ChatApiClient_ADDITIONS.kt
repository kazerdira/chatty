// ============================================================================
// ADDITIONS TO ChatApiClient.kt
// Location: shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt
// ============================================================================

// ADD THIS METHOD after the existing createRoom() method (around line 200):

/**
 * ✅ PROFESSIONAL FIX: Send message via HTTP API (reliable, like room creation!)
 * 
 * This is the primary method for sending messages.
 * HTTP API provides guaranteed delivery, WebSocket is bonus for real-time.
 * 
 * @param roomId The room ID to send the message to
 * @param content The message content (text, image, etc.)
 * @param replyToId Optional message ID to reply to
 * @return Result with the sent message DTO
 */
suspend fun sendMessageViaHttp(
    roomId: String,
    content: MessageContentDto,
    replyToId: String? = null
): Result<MessageDto> {
    return safeApiCall {
        println("📤 ChatApiClient: Sending message via HTTP to room $roomId")
        val response = httpClient.post("$baseUrl/messages") {
            bearerAuth(tokenManager.getAccessToken() ?: "")
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(
                roomId = roomId,
                content = content,
                replyToId = replyToId
            ))
        }
        val message: MessageDto = response.body()
        println("✅ ChatApiClient: Message sent successfully via HTTP: ${message.id}")
        message
    }
}

// ============================================================================
// ADD THIS DTO at the bottom of the file (after existing DTOs):
// ============================================================================

// Note: This might already exist in dto/MessageDto.kt - check first!
// If it exists there, you can skip this and just add an import

@Serializable
data class SendMessageRequest(
    val roomId: String,
    val content: MessageContentDto,
    val replyToId: String? = null
)

// ============================================================================
// THAT'S IT! No other changes needed to ChatApiClient
// ============================================================================

// The existing methods remain unchanged:
// - connectWebSocket() - Still works for real-time sync
// - sendClientMessage() - Still used for typing indicators, etc.
// - sendMessage() - Can be deprecated or kept for direct WebSocket sends
//
// Key point: HTTP is now the PRIMARY method, WebSocket is SECONDARY
