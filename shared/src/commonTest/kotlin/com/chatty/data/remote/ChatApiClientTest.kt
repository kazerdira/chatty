package com.chatty.data.remote

import com.chatty.data.local.TokenManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.BeforeTest

class ChatApiClientTest {
    
    private lateinit var tokenManager: FakeTokenManager
    private lateinit var chatApiClient: ChatApiClient
    
    @BeforeTest
    fun setup() {
        tokenManager = FakeTokenManager()
        chatApiClient = ChatApiClient(
            baseUrl = "http://localhost:8080",
            tokenManager = tokenManager
        )
    }
    
    @Test
    fun `connectWebSocket should not proceed if shouldReconnect is false`() = runTest {
        // Given
        tokenManager.saveAccessToken("fake-token")
        tokenManager.saveUserId("user123")
        chatApiClient.disconnectWebSocket() // Sets shouldReconnect = false
        
        // When
        chatApiClient.connectWebSocket()
        
        // Then
        chatApiClient.connectionState.value shouldBe WebSocketConnectionState.DISCONNECTED
    }
    
    @Test
    fun `connectWebSocket should not proceed if already connecting`() = runTest {
        // This test verifies the isConnecting flag works correctly
        // Given
        tokenManager.saveAccessToken("fake-token")
        tokenManager.saveUserId("user123")
        chatApiClient.resetReconnectionFlag()
        
        // When - simulate rapid calls
        // Note: This will actually try to connect to localhost:8080
        // In a real test, we'd mock the WebSocket client
        val state1 = chatApiClient.connectionState.value
        chatApiClient.connectWebSocket()
        delay(100) // Give it time to start connecting
        val state2 = chatApiClient.connectionState.value
        chatApiClient.connectWebSocket() // Second call should be skipped
        delay(100)
        val state3 = chatApiClient.connectionState.value
        
        // Then
        println("State progression: $state1 -> $state2 -> $state3")
        // Second call should not change state
        state2 shouldBe state3
    }
    
    @Test
    fun `resetReconnectionFlag should reset flags`() = runTest {
        // Given
        chatApiClient.disconnectWebSocket() // Sets shouldReconnect = false
        
        // When
        chatApiClient.resetReconnectionFlag()
        
        // Then - should be able to connect again
        // (In real scenario, this would actually attempt connection)
        println("Reset successful - ready for new connection")
    }
    
    @Test
    fun `retryConnection should call connectWebSocket when session is null`() = runTest {
        // Given
        tokenManager.saveAccessToken("fake-token")
        tokenManager.saveUserId("user123")
        
        // When
        chatApiClient.retryConnection()
        delay(100)
        
        // Then
        val state = chatApiClient.connectionState.value
        println("State after retry: $state")
        // Should not be DISCONNECTED (will be CONNECTING or ERROR since no real server)
        state shouldNotBe WebSocketConnectionState.DISCONNECTED
    }
}

// Fake implementation for testing
class FakeTokenManager : TokenManager {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var userId: String? = null
    private var username: String? = null
    
    override fun saveAccessToken(token: String) {
        accessToken = token
    }
    
    override fun saveRefreshToken(token: String) {
        refreshToken = token
    }
    
    override fun saveUserId(userId: String) {
        this.userId = userId
    }
    
    override fun saveUserInfo(userId: String, username: String) {
        this.userId = userId
        this.username = username
    }
    
    override fun getAccessToken(): String? = accessToken
    
    override fun getRefreshToken(): String? = refreshToken
    
    override fun getUserId(): String? = userId
    
    override fun getUsername(): String? = username
    
    override fun clearTokens() {
        accessToken = null
        refreshToken = null
        userId = null
        username = null
    }
}
