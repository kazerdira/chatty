// REPLACE the connectWebSocket() function in ChatApiClient.kt with this improved version:

suspend fun connectWebSocket() {
    if (isConnecting) {
        println("🔌 WebSocket: Already connecting, skipping...")
        return
    }
    
    if (websocketSession != null) {
        println("🔌 WebSocket: Already connected, skipping...")
        return
    }
    
    isConnecting = true
    _connectionState.value = if (reconnectAttempt > 0) {
        WebSocketConnectionState.RECONNECTING
    } else {
        WebSocketConnectionState.CONNECTING
    }
    
    println("🔌 WebSocket: Connecting... (attempt ${reconnectAttempt + 1})")
    
    try {
        // Wait a bit to ensure tokens are fully saved and available
        delay(300)
        
        val token = tokenManager.getAccessToken()
        val userId = tokenManager.getUserId()
        
        // Validate prerequisites
        if (token == null) {
            println("❌ WebSocket: No access token found!")
            throw IllegalStateException("No access token available")
        }
        
        if (userId == null) {
            println("❌ WebSocket: No user ID found!")
            throw IllegalStateException("No user ID - please logout and login again")
        }
        
        println("🔐 WebSocket: Connecting with userId: $userId, token: ${token.take(15)}...")
        
        websocketSession = websocketClient.webSocketSession {
            url {
                protocol = URLProtocol.WS
                host = baseUrl.removePrefix("http://").removePrefix("https://")
                path("/ws")
            }
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        
        _connectionState.value = WebSocketConnectionState.CONNECTED
        reconnectAttempt = 0
        println("✅ WebSocket: Connected successfully")
        
        // Send authentication message with user ID
        val authMessage = ClientWebSocketMessage.Authenticate(userId)
        val authJson = Json.encodeToString(ClientWebSocketMessage.serializer(), authMessage)
        websocketSession?.send(Frame.Text(authJson))
        println("🔐 WebSocket: Authentication message sent for user: $userId")
        
        // Start listening to messages
        websocketSession?.incoming?.consumeAsFlow()?.collect { frame ->
            when (frame) {
                is Frame.Text -> {
                    try {
                        val text = frame.readText()
                        val message = Json.decodeFromString<WebSocketMessage>(text)
                        println("📨 WebSocket: Received message: ${message::class.simpleName}")
                        _incomingMessages.emit(message)
                    } catch (e: Exception) {
                        println("⚠️ WebSocket: Error parsing message: ${e.message}")
                        e.printStackTrace()
                    }
                }
                is Frame.Close -> {
                    val reason = closeReason.await()
                    println("🔌 WebSocket: Connection closed by server: $reason")
                    websocketSession = null
                    _connectionState.value = WebSocketConnectionState.DISCONNECTED
                }
                else -> {}
            }
        }
        
        // If collect finishes, connection was closed
        println("🔌 WebSocket: Connection closed")
        websocketSession = null
        _connectionState.value = WebSocketConnectionState.DISCONNECTED
        reconnectWithBackoff()
        
    } catch (e: Exception) {
        println("❌ WebSocket: Connection error: ${e.message}")
        e.printStackTrace()
        websocketSession = null
        _connectionState.value = WebSocketConnectionState.ERROR
        reconnectWithBackoff()
    } finally {
        isConnecting = false
    }
}
