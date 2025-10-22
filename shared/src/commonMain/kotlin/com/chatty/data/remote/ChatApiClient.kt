package com.chatty.data.remote

import com.chatty.data.remote.dto.*
import com.chatty.data.local.TokenManager
import io.ktor.client.*
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
    
    private val websocketClient = HttpClient {
        install(WebSockets) {
            pingInterval = 15_000
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }
    
    private var websocketSession: DefaultClientWebSocketSession? = null
    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(replay = 0)
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()
    
    private var isConnecting = false
    
    suspend fun connectWebSocket() {
        if (isConnecting || websocketSession != null) return
        
        isConnecting = true
        try {
            val token = tokenManager.getAccessToken() ?: throw IllegalStateException("No access token")
            
            websocketSession = websocketClient.webSocketSession {
                url {
                    protocol = URLProtocol.WS
                    host = baseUrl.removePrefix("http://").removePrefix("https://")
                    path("/ws")
                }
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            
            // Start listening to messages
            websocketSession?.incoming?.consumeAsFlow()?.collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val text = frame.readText()
                            val message = Json.decodeFromString<WebSocketMessage>(text)
                            _incomingMessages.emit(message)
                        } catch (e: Exception) {
                            println("Error parsing WebSocket message: ${e.message}")
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            println("WebSocket connection error: ${e.message}")
            reconnectWithBackoff()
        } finally {
            isConnecting = false
        }
    }
    
    private suspend fun reconnectWithBackoff() {
        var backoffDelay = 1000L
        val maxDelay = 32000L
        
        while (true) {
            delay(backoffDelay)
            
            try {
                connectWebSocket()
                break
            } catch (e: Exception) {
                backoffDelay = min(backoffDelay * 2, maxDelay)
            }
        }
    }
    
    suspend fun sendMessage(message: WebSocketMessage): Result<Unit> {
        return runCatching {
            val session = websocketSession ?: throw IllegalStateException("WebSocket not connected")
            val text = Json.encodeToString(message)
            session.send(Frame.Text(text))
        }
    }
    
    suspend fun disconnectWebSocket() {
        websocketSession?.close()
        websocketSession = null
    }
    
    // HTTP API methods
    suspend fun login(request: AuthRequest): Result<AuthResponse> {
        println("🔐 ChatApiClient: Attempting login for user: ${request.username}")
        println("🔐 ChatApiClient: URL: $baseUrl/auth/login")
        return safeApiCall {
            val response = httpClient.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            println("🔐 ChatApiClient: Login response status: ${response.status}")
            val body: AuthResponse = response.body()
            println("🔐 ChatApiClient: Login successful! Token: ${body.token.take(20)}...")
            
            // Save user info to TokenManager
            tokenManager.saveUserInfo(
                userId = body.userId,
                username = body.username,
                displayName = body.displayName
            )
            println("💾 ChatApiClient: User info saved - ID: ${body.userId}, Username: ${body.username}")
            
            body
        }
    }
    
    suspend fun register(request: RegisterRequest): Result<AuthResponse> {
        println("📝 ChatApiClient: Attempting registration for user: ${request.username}")
        println("📝 ChatApiClient: Display name: ${request.displayName}")
        println("📝 ChatApiClient: URL: $baseUrl/auth/register")
        return safeApiCall {
            val response = httpClient.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            println("📝 ChatApiClient: Register response status: ${response.status}")
            val body: AuthResponse = response.body()
            println("📝 ChatApiClient: Registration successful! Token: ${body.token.take(20)}...")
            
            // Save user info to TokenManager
            tokenManager.saveUserInfo(
                userId = body.userId,
                username = body.username,
                displayName = body.displayName
            )
            println("💾 ChatApiClient: User info saved - ID: ${body.userId}, Username: ${body.username}")
            
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
            httpClient.post("$baseUrl/rooms") {
                bearerAuth(tokenManager.getAccessToken() ?: "")
                contentType(ContentType.Application.Json)
                setBody(CreateRoomRequest(
                    name = name,
                    type = type,
                    participantIds = participantIds
                ))
            }.body()
        }
    }
    
    suspend fun getMessages(
        roomId: String,
        before: String? = null,
        limit: Int = 50
    ): Result<List<MessageDto>> {
        return safeApiCall {
            httpClient.get("$baseUrl/rooms/$roomId/messages") {
                bearerAuth(tokenManager.getAccessToken() ?: "")
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
            println("✅ API call successful")
            Result.success(result)
        } catch (e: Exception) {
            println("❌ API call failed: ${e.message}")
            println("❌ Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
