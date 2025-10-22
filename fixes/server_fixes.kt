// ========================
// ADD THESE ROUTE CHANGES TO Application.kt
// ========================

// 1. UPDATE userRoutes function - Add /users/me endpoint
fun Route.userRoutes(userRepository: UserRepository) {
    route("/users") {
        // NEW: Get current authenticated user
        get("/me") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("userId").asString()
            
            val user = userRepository.getUserById(userId)
                ?: throw NotFoundException("User not found")
            
            call.respond(HttpStatusCode.OK, user)
        }
        
        get("/search") {
            val query = call.request.queryParameters["q"] ?: ""
            
            if (query.length < 2) {
                throw ValidationException(
                    "Query too short",
                    mapOf("q" to "Search query must be at least 2 characters")
                )
            }
            
            val users = userRepository.searchUsers(query)
            call.respond(HttpStatusCode.OK, users)
        }
        
        get("/{id}") {
            val userId = call.parameters["id"]
                ?: throw ValidationException(
                    "Missing user ID",
                    mapOf("id" to "User ID is required")
                )
            
            val user = userRepository.getUserById(userId)
                ?: throw NotFoundException("User not found")
            
            call.respond(HttpStatusCode.OK, user)
        }
    }
}

// 2. UPDATE webSocketRoute - Fix authentication
fun Route.webSocketRoute(
    webSocketManager: WebSocketManager,
    userRepository: UserRepository,
    messageService: MessageService
) {
    webSocket("/ws") {
        var currentUserId: String? = null
        val sessionId = java.util.UUID.randomUUID().toString()
        
        try {
            application.log.info("New WebSocket connection: $sessionId")
            
            // Wait for authentication
            var authenticated = false
            var authTimeout = 0
            
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val text = frame.readText()
                            application.log.info("📨 Received: $text")
                            
                            val message = Json.decodeFromString<ClientWebSocketMessage>(text)
                            
                            when (message) {
                                is ClientWebSocketMessage.Authenticate -> {
                                    currentUserId = message.userId
                                    webSocketManager.addConnection(message.userId, this)
                                    authenticated = true
                                    application.log.info("✅ User ${message.userId} authenticated")
                                    
                                    val authSuccess = WebSocketMessage.AuthenticationSuccess(
                                        userId = message.userId,
                                        timestamp = Clock.System.now().toString()
                                    )
                                    outgoing.send(Frame.Text(Json.encodeToString(WebSocketMessage.serializer(), authSuccess)))
                                }
                                
                                is ClientWebSocketMessage.JoinRoom -> {
                                    if (!authenticated) {
                                        outgoing.send(Frame.Text(Json.encodeToString(
                                            WebSocketMessage.serializer(),
                                            WebSocketMessage.Error("Not authenticated")
                                        )))
                                        continue
                                    }
                                    
                                    currentUserId?.let { userId ->
                                        webSocketManager.joinRoom(userId, message.roomId)
                                        application.log.info("📍 User $userId joined room ${message.roomId}")
                                    }
                                }
                                
                                is ClientWebSocketMessage.SendMessage -> {
                                    if (!authenticated) {
                                        outgoing.send(Frame.Text(Json.encodeToString(
                                            WebSocketMessage.serializer(),
                                            WebSocketMessage.Error("Not authenticated")
                                        )))
                                        continue
                                    }
                                    
                                    currentUserId?.let { userId ->
                                        val sendRequest = SendMessageRequest(
                                            roomId = message.roomId,
                                            content = message.content,
                                            replyToId = null
                                        )
                                        
                                        try {
                                            val sentMessage = messageService.sendMessage(userId, sendRequest)
                                            
                                            sentMessage?.let {
                                                outgoing.send(Frame.Text(Json.encodeToString(
                                                    WebSocketMessage.serializer(),
                                                    WebSocketMessage.MessageSent(
                                                        tempId = message.messageId,
                                                        message = it
                                                    )
                                                )))
                                            }
                                        } catch (e: Exception) {
                                            application.log.error("Failed to send message", e)
                                            outgoing.send(Frame.Text(Json.encodeToString(
                                                WebSocketMessage.serializer(),
                                                WebSocketMessage.Error("Failed: ${e.message}")
                                            )))
                                        }
                                    }
                                }
                                
                                is ClientWebSocketMessage.TypingIndicator -> {
                                    if (!authenticated) continue
                                    
                                    currentUserId?.let { userId ->
                                        val user = userRepository.getUserById(userId)
                                        webSocketManager.broadcastToRoom(
                                            roomId = message.roomId,
                                            message = WebSocketMessage.TypingIndicator(
                                                roomId = message.roomId,
                                                userId = userId,
                                                username = user?.displayName ?: "Unknown",
                                                isTyping = message.isTyping
                                            ),
                                            excludeUserId = userId
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            application.log.error("Error processing WebSocket message", e)
                            outgoing.send(Frame.Text(Json.encodeToString(
                                WebSocketMessage.serializer(),
                                WebSocketMessage.Error("Parse error: ${e.message}")
                            )))
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            application.log.error("WebSocket error", e)
        } finally {
            currentUserId?.let { userId ->
                webSocketManager.removeConnection(userId, this)
            }
        }
    }
}
