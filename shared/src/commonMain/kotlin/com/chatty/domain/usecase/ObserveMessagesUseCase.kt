package com.chatty.domain.usecase

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import com.chatty.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow

class ObserveMessagesUseCase(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(roomId: ChatRoom.RoomId): Flow<List<Message>> {
        return messageRepository.observeMessages(roomId)
    }
}
