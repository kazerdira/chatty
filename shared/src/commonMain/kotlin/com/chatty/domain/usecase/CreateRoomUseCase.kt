package com.chatty.domain.usecase

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.User
import com.chatty.domain.repository.ChatRoomRepository

class CreateRoomUseCase(
    private val roomRepository: ChatRoomRepository
) {
    suspend operator fun invoke(params: CreateRoomParams): Result<ChatRoom> {
        return roomRepository.createRoom(
            name = params.name,
            type = params.type,
            participantIds = params.participantIds
        )
    }
    
    data class CreateRoomParams(
        val name: String,
        val type: ChatRoom.RoomType,
        val participantIds: List<User.UserId>
    )
}
