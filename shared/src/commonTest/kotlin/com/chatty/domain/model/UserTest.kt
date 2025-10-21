package com.chatty.domain.model

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserTest {
    
    @Test
    fun `test user creation`() {
        // Given
        val userId = User.UserId("test-user-123")
        val username = "testuser"
        val displayName = "Test User"
        val now = Clock.System.now()
        
        // When
        val user = User(
            id = userId,
            username = username,
            displayName = displayName,
            avatarUrl = null,
            status = User.UserStatus.ONLINE,
            lastSeen = now,
            createdAt = now
        )
        
        // Then
        assertNotNull(user)
        assertEquals(userId, user.id)
        assertEquals(username, user.username)
        assertEquals(displayName, user.displayName)
        assertEquals(User.UserStatus.ONLINE, user.status)
    }
    
    @Test
    fun `test user status enum`() {
        // When/Then
        assertEquals(3, User.UserStatus.values().size)
        assertNotNull(User.UserStatus.ONLINE)
        assertNotNull(User.UserStatus.AWAY)
        assertNotNull(User.UserStatus.OFFLINE)
    }
    
    @Test
    fun `test user id value class`() {
        // Given
        val id1 = User.UserId("123")
        val id2 = User.UserId("123")
        val id3 = User.UserId("456")
        
        // Then
        assertEquals(id1, id2)
        assertEquals(id1.value, id2.value)
        assertEquals("123", id1.value)
        assertEquals("456", id3.value)
    }
}
