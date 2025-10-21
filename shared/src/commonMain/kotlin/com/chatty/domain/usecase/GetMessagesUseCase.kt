package com.chatty.domain.usecase

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import com.chatty.domain.repository.MessageRepository

class GetMessagesUseCase(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(
        roomId: ChatRoom.RoomId,
        before: Message.MessageId? = null,
        limit: Int = 50
    ): Result<List<Message>> {
        return messageRepository.getMessages(roomId, before, limit)
    }
}
