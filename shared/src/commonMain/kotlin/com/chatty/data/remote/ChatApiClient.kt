package com.chatty.data.remote

import com.chatty.data.remote.dto.*
import com.chatty.data.local.TokenManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.math.min

enum class WebSocketConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}

class ChatApiClient(
    private val tokenManager: TokenManager,
    private val baseUrl: String = "http://localhost:8080"
) {
    init {
        println("🌐 ChatApiClient initialized with baseUrl: $baseUrl")
    }
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        
        install(Logging) {
            level = LogLevel.INFO
        }
        
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
    }
    
    private val websocketClient = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 15_000
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }
    
    private var websocketSession: DefaultClientWebSocketSession? = null
    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(replay = 0)
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()
    
    private val _connectionState = MutableStateFlow(WebSocketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()
    
    private var isConnecting = false
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 10
    private var shouldReconnect = true // Flag to control reconnection
    
    suspend fun connectWebSocket() {
        println("🔌 WebSocket: connectWebSocket() called - shouldReconnect=$shouldReconnect, isConnecting=$isConnecting, state=${_connectionState.value}")
        
        if (!shouldReconnect) {
            println("🔌 WebSocket: Reconnection disabled (logged out)")
            return
        }
        
        if (isConnecting) {
            println("🔌 WebSocket: Already connecting, skipping...")
            return
        }
        
        if (websocketSession != null && _connectionState.value == WebSocketConnectionState.CONNECTED) {
            println("🔌 WebSocket: Already connected, skipping...")
            return
        }
        
        try {
            isConnecting = true
            _connectionState.value = if (reconnectAttempt > 0) {
                WebSocketConnectionState.RECONNECTING
            } else {
                WebSocketConnectionState.CONNECTING
            }
            
            println("🔌 WebSocket: Connecting... (attempt ${reconnectAttempt + 1})")
            // Validate prerequisites
            val token = tokenManager.getAccessToken()
            val userId = tokenManager.getUserId()
            
            if (token == null) {
                println("❌ WebSocket: No access token found!")
                throw IllegalStateException("No access token available")
            }
            
            if (userId == null) {
                println("❌ WebSocket: No user ID found!")
                throw IllegalStateException("No user ID - please logout and login again")
            }
            
            println("🔐 WebSocket: Connecting with userId: $userId")
            
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
            
            // Send authentication message
            val authMessage = ClientWebSocketMessage.Authenticate(userId)
            val authJson = Json.encodeToString(ClientWebSocketMessage.serializer(), authMessage)
            websocketSession?.send(Frame.Text(authJson))
            println("🔐 WebSocket: Authentication message sent")
            
            // Start listening to messages
            websocketSession?.incoming?.consumeAsFlow()?.collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val text = frame.readText()
                            val message = Json.decodeFromString<WebSocketMessage>(text)
                            println("📨 WebSocket: Received ${message::class.simpleName}")
                            _incomingMessages.emit(message)
                        } catch (e: Exception) {
                            println("⚠️ WebSocket: Parse error: ${e.message}")
                        }
                    }
                    is Frame.Close -> {
                        println("🔌 WebSocket: Connection closed by server")
                        websocketSession = null
                        _connectionState.value = WebSocketConnectionState.DISCONNECTED
                    }
                    else -> {}
                }
            }
            
            // Connection closed
            println("🔌 WebSocket: Connection closed")
            websocketSession = null
            _connectionState.value = WebSocketConnectionState.DISCONNECTED
            
            if (shouldReconnect) {
                reconnectWithBackoff()
            }
            
        } catch (e: Exception) {
            println("❌ WebSocket: Connection error: ${e.message}")
            e.printStackTrace()
            websocketSession = null
            _connectionState.value = WebSocketConnectionState.ERROR
            
            if (shouldReconnect) {
                reconnectWithBackoff()
            }
        } finally {
            isConnecting = false
        }
    }
    
    private suspend fun reconnectWithBackoff() {
        if (!shouldReconnect) {
            println("🔌 WebSocket: Reconnection disabled")
            return
        }
        
        if (reconnectAttempt >= maxReconnectAttempts) {
            println("❌ WebSocket: Max reconnection attempts reached")
            _connectionState.value = WebSocketConnectionState.ERROR
            return
        }
        
        reconnectAttempt++
        val backoffDelay = min(1000L * (1 shl (reconnectAttempt - 1)), 32000L)
        
        println("⏳ WebSocket: Reconnecting in ${backoffDelay / 1000}s (attempt $reconnectAttempt)")
        _connectionState.value = WebSocketConnectionState.RECONNECTING
        
        delay(backoffDelay)
        connectWebSocket()
    }
    
    suspend fun sendMessage(message: WebSocketMessage): Result<Unit> {
        return runCatching {
            val session = websocketSession ?: throw IllegalStateException("WebSocket not connected")
            val text = Json.encodeToString(message)
            println("📤 WebSocket: Sending ${message::class.simpleName}")
            session.send(Frame.Text(text))
        }
    }
    
    suspend fun sendClientMessage(message: ClientWebSocketMessage): Result<Unit> {
        return runCatching {
            val session = websocketSession ?: throw IllegalStateException("WebSocket not connected")
            val text = Json.encodeToString(ClientWebSocketMessage.serializer(), message)
            println("📤 WebSocket: Sending ${message::class.simpleName}")
            session.send(Frame.Text(text))
        }
    }
    
    suspend fun joinRoom(roomId: String) {
        val message = ClientWebSocketMessage.JoinRoom(roomId)
        sendClientMessage(message).onSuccess {
            println("✅ Joined room: $roomId")
        }.onFailure { error ->
            println("❌ Failed to join room: ${error.message}")
        }
    }
    
    suspend fun disconnectWebSocket() {
        println("🔌 WebSocket: Disconnecting...")
        shouldReconnect = false // Disable reconnection
        reconnectAttempt = maxReconnectAttempts
        websocketSession?.close()
        websocketSession = null
        _connectionState.value = WebSocketConnectionState.DISCONNECTED
        println("✅ WebSocket: Disconnected")
    }
    
    suspend fun retryConnection() {
        println("🔄 WebSocket: Manual retry requested")
        println("🔄 WebSocket: websocketSession=$websocketSession, shouldReconnect=$shouldReconnect")
        shouldReconnect = true // Re-enable reconnection
        reconnectAttempt = 0
        if (websocketSession == null) {
            println("🔄 WebSocket: Calling connectWebSocket()...")
            connectWebSocket()
        } else {
            println("🔄 WebSocket: Session exists, not reconnecting")
        }
    }
    
    fun resetReconnectionFlag() {
        shouldReconnect = true
        reconnectAttempt = 0
        println("🔄 WebSocket: Reconnection flag reset for new session")
    }
    
    // HTTP API methods
    suspend fun login(request: AuthRequest): Result<AuthResponse> {
        println("🔐 ChatApiClient: Login attempt for: ${request.username}")
        return safeApiCall {
            val response = httpClient.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val body: AuthResponse = response.body()
            println("✅ ChatApiClient: Login successful - ${body.userId}")
            
            // Save user info
            tokenManager.saveUserInfo(
                userId = body.userId,
                username = body.username,
                displayName = body.displayName
            )
            
            body
        }
    }
    
    suspend fun register(request: RegisterRequest): Result<AuthResponse> {
        println("📝 ChatApiClient: Registration attempt for: ${request.username}")
        return safeApiCall {
            val response = httpClient.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val body: AuthResponse = response.body()
            println("✅ ChatApiClient: Registration successful - ${body.userId}")
            
            // Save user info
            tokenManager.saveUserInfo(
                userId = body.userId,
                username = body.username,
                displayName = body.displayName
            )
            
            body
        }
    }
    
    suspend fun refreshToken(request: RefreshTokenRequest): Result<AuthResponse> {
        return safeApiCall {
            httpClient.post("$baseUrl/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }
    
    suspend fun getRooms(): Result<List<ChatRoomDto>> {
        return safeApiCall {
            httpClient.get("$baseUrl/rooms") {
                bearerAuth(tokenManager.getAccessToken() ?: "")
            }.body()
        }
    }
    
    suspend fun createRoom(
        name: String,
        type: String,
        participantIds: List<String>
    ): Result<ChatRoomDto> {
        return safeApiCall {
            println("📝 API: Creating room - name: $name, type: $type, participants: ${participantIds.size}")
            val response = httpClient.post("$baseUrl/rooms") {
                bearerAuth(tokenManager.getAccessToken() ?: "")
                contentType(ContentType.Application.Json)
                setBody(CreateRoomRequest(
                    name = name,
                    type = type,
                    participantIds = participantIds
                ))
            }
            val room: ChatRoomDto = response.body()
            println("✅ API: Room created - ${room.id}")
            room
        }
    }
    
    /**
     * ✅ fix6_v2: Send message via HTTP API (reliable, like room creation!)
     * 
     * This is the primary method for sending messages.
     * HTTP API provides guaranteed delivery, WebSocket is bonus for real-time.
     * 
     * @param roomId The room ID to send the message to
     * @param content The message content (text, image, etc.)
     * @param replyToId Optional message ID to reply to
     * @return Result with the sent message DTO
     */
    suspend fun sendMessageViaHttp(
        roomId: String,
        content: MessageContentDto,
        replyToId: String? = null
    ): Result<MessageDto> {
        return safeApiCall {
            println("📤 HTTP: Sending message to room $roomId")
            
            val serverContent = content.toServerDto()
            println("📤 HTTP: Content - type=${serverContent.type}, text=${serverContent.text}, url=${serverContent.url}")
            
            val response = httpClient.post("$baseUrl/messages") {
                bearerAuth(tokenManager.getAccessToken() ?: "")
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(
                    roomId = roomId,
                    content = serverContent,
                    replyToId = replyToId
                ))
            }
            
            val message: MessageDto = response.body()
            println("✅ HTTP: Message sent successfully: ${message.id}")
            message
        }
    }
    
    suspend fun getMessages(
        roomId: String,
        before: String? = null,
        limit: Int = 50
    ): Result<List<MessageDto>> {
        return safeApiCall {
            httpClient.get("$baseUrl/messages") {
                bearerAuth(tokenManager.getAccessToken() ?: "")
                parameter("roomId", roomId)
                parameter("limit", limit)
                before?.let { parameter("before", it) }
            }.body()
        }
    }
    
    suspend fun searchUsers(query: String): Result<List<UserDto>> {
        return safeApiCall {
            httpClient.get("$baseUrl/users/search") {
                bearerAuth(tokenManager.getAccessToken() ?: "")
                parameter("q", query)
            }.body()
        }
    }
    
    private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> {
        return try {
            val result = block()
            Result.success(result)
        } catch (e: ClientRequestException) {
            val errorMessage = when (e.response.status.value) {
                400 -> "Invalid request. Please check your input."
                401 -> "Authentication failed. Please log in again."
                403 -> "You don't have permission to access this resource."
                404 -> "Resource not found."
                409 -> "Conflict. This resource already exists."
                422 -> "Validation failed. Please check your input."
                429 -> "Too many requests. Please try again later."
                else -> "Request failed: ${e.response.status.description}"
            }
            println("❌ Client error (${e.response.status.value}): $errorMessage")
            Result.failure(Exception(errorMessage))
        } catch (e: ServerResponseException) {
            val errorMessage = when (e.response.status.value) {
                500 -> "Server error. Please try again later."
                502 -> "Bad gateway. Server is temporarily unavailable."
                503 -> "Service unavailable. Please try again later."
                504 -> "Gateway timeout. Please try again."
                else -> "Server error: ${e.response.status.description}"
            }
            println("❌ Server error (${e.response.status.value}): $errorMessage")
            Result.failure(Exception(errorMessage))
        } catch (e: HttpRequestTimeoutException) {
            println("❌ Request timeout")
            Result.failure(Exception("Request timed out. Please check your connection and try again."))
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Unable to resolve host") == true -> 
                    "Network error. Please check your internet connection."
                e.message?.contains("Connection refused") == true -> 
                    "Cannot connect to server. Please make sure the server is running."
                e.message?.contains("Connection reset") == true -> 
                    "Connection lost. Please try again."
                else -> e.message ?: "Unknown error occurred. Please try again."
            }
            println("❌ API call failed: $errorMessage")
            e.printStackTrace()
            Result.failure(Exception(errorMessage))
        }
    }
}
