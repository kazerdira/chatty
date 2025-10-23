package com.chatty.domain.usecase

import com.chatty.data.remote.ChatApiClient
import com.chatty.domain.model.ChatRoom

class JoinRoomUseCase(
    private val apiClient: ChatApiClient
) {
    suspend operator fun invoke(roomId: ChatRoom.RoomId) {
        apiClient.joinRoom(roomId.value)
    }
}
