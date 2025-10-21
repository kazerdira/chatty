package com.chatty.domain.usecase

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.repository.ChatRoomRepository
import kotlinx.coroutines.flow.Flow

class ObserveRoomsUseCase(
    private val roomRepository: ChatRoomRepository
) {
    operator fun invoke(): Flow<List<ChatRoom>> {
        return roomRepository.observeRooms()
    }
}
