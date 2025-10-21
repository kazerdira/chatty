package com.chatty.domain.model

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessageTest {
    
    @Test
    fun `test text message creation`() {
        // Given
        val messageId = Message.MessageId("msg-123")
        val roomId = ChatRoom.RoomId("room-456")
        val senderId = User.UserId("user-789")
        val text = "Hello, World!"
        val now = Clock.System.now()
        
        // When
        val message = Message(
            id = messageId,
            roomId = roomId,
            senderId = senderId,
            content = Message.MessageContent.Text(text),
            timestamp = now,
            status = Message.MessageStatus.SENT
        )
        
        // Then
        assertNotNull(message)
        assertEquals(messageId, message.id)
        assertEquals(roomId, message.roomId)
        assertEquals(senderId, message.senderId)
        assertEquals(Message.MessageStatus.SENT, message.status)
        
        val content = message.content as Message.MessageContent.Text
        assertEquals(text, content.text)
    }
    
    @Test
    fun `test message status enum`() {
        // When/Then
        assertEquals(5, Message.MessageStatus.values().size)
        assertNotNull(Message.MessageStatus.SENDING)
        assertNotNull(Message.MessageStatus.SENT)
        assertNotNull(Message.MessageStatus.DELIVERED)
        assertNotNull(Message.MessageStatus.READ)
        assertNotNull(Message.MessageStatus.FAILED)
    }
    
    @Test
    fun `test image message content`() {
        // Given
        val url = "https://example.com/image.jpg"
        val thumbnail = "https://example.com/thumb.jpg"
        
        // When
        val content = Message.MessageContent.Image(
            url = url,
            thumbnailUrl = thumbnail,
            width = 1920,
            height = 1080
        )
        
        // Then
        assertEquals(url, content.url)
        assertEquals(thumbnail, content.thumbnailUrl)
        assertEquals(1920, content.width)
        assertEquals(1080, content.height)
    }
}
