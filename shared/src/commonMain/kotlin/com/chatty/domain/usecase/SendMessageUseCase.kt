package com.chatty.domain.usecase

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import com.chatty.domain.model.User
import com.chatty.domain.repository.ChatRoomRepository
import com.chatty.domain.repository.MessageRepository
import kotlinx.datetime.Clock
import java.util.UUID

class SendMessageUseCase(
    private val messageRepository: MessageRepository,
    private val roomRepository: ChatRoomRepository
) {
    suspend operator fun invoke(params: SendMessageParams): Result<Message> {
        return runCatching {
            // Validate room exists
            val room = roomRepository.getRoom(params.roomId)
                ?: throw IllegalArgumentException("Room not found")
            
            // Create message
            val message = Message(
                id = Message.MessageId(UUID.randomUUID().toString()),
                roomId = params.roomId,
                senderId = params.senderId,
                content = params.content,
                timestamp = Clock.System.now(),
                status = Message.MessageStatus.SENDING,
                editedAt = null,
                replyTo = params.replyTo
            )
            
            // Send message
            messageRepository.sendMessage(message).getOrThrow()
        }
    }
    
    data class SendMessageParams(
        val roomId: ChatRoom.RoomId,
        val senderId: User.UserId,
        val content: Message.MessageContent,
        val replyTo: Message.MessageId? = null
    )
}
