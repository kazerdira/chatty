package com.chatty.domain.repository

import com.chatty.domain.model.ChatRoom
import com.chatty.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun sendMessage(message: Message): Result<Message>
    suspend fun getMessage(messageId: Message.MessageId): Message?
    suspend fun getMessages(
        roomId: ChatRoom.RoomId,
        before: Message.MessageId? = null,
        limit: Int = 50
    ): Result<List<Message>>
    fun observeMessages(roomId: ChatRoom.RoomId): Flow<List<Message>>
    suspend fun markAsRead(messageIds: List<Message.MessageId>): Result<Unit>
    suspend fun deleteMessage(messageId: Message.MessageId): Result<Unit>
    suspend fun editMessage(
        messageId: Message.MessageId,
        newContent: Message.MessageContent
    ): Result<Message>
    suspend fun syncMessages(roomId: ChatRoom.RoomId): Result<Unit>
}
