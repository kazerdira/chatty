package com.chatty.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.chatty.server.data.DatabaseFactory
import com.chatty.server.data.repository.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.Duration

fun main() {
    // Initialize database
    DatabaseFactory.init()
    
    val config = AppConfig.load()
    
    embeddedServer(
        Netty, 
        port = config.server.port, 
        host = config.server.host, 
        module = { module(config) }
    ).start(wait = true)
}

// ========================
// CONFIGURATION
// ========================

object AppConfig {
    data class Config(
        val server: ServerConfig,
        val jwt: JwtConfig,
        val database: DatabaseConfig
    )
    
    data class ServerConfig(
        val port: Int = 8080,
        val host: String = "0.0.0.0"
    )
    
    data class JwtConfig(
        val secret: String,
        val issuer: String = "chatty-server",
        val audience: String = "chatty-users",
        val realm: String = "Chatty App",
        val accessTokenExpiry: Long = 3600000, // 1 hour
        val refreshTokenExpiry: Long = 604800000 // 7 days
    )
    
    data class DatabaseConfig(
        val url: String = "jdbc:postgresql://localhost:5432/chatty",
        val driver: String = "org.postgresql.Driver"
    )
    
    fun load(): Config {
        val jwtSecret = System.getenv("JWT_SECRET") 
            ?: "chatty-dev-secret-change-in-production"
        
        return Config(
            server = ServerConfig(),
            jwt = JwtConfig(secret = jwtSecret),
            database = DatabaseConfig()
        )
    }
}

// ========================
// VALIDATION
// ========================

object Validator {
    fun validateUsername(username: String): ValidationResult {
        return when {
            username.isBlank() -> ValidationResult.Error("Username cannot be empty")
            username.length < 3 -> ValidationResult.Error("Username must be at least 3 characters")
            username.length > 20 -> ValidationResult.Error("Username must be less than 20 characters")
            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> 
                ValidationResult.Error("Username can only contain letters, numbers, and underscores")
            else -> ValidationResult.Success
        }
    }
    
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Error("Password cannot be empty")
            password.length < 6 -> ValidationResult.Error("Password must be at least 6 characters")
            else -> ValidationResult.Success
        }
    }
    
    fun validateDisplayName(displayName: String): ValidationResult {
        return when {
            displayName.isBlank() -> ValidationResult.Error("Display name cannot be empty")
            displayName.length > 50 -> ValidationResult.Error("Display name must be less than 50 characters")
            else -> ValidationResult.Success
        }
    }
    
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}

// ========================
// DATA MODELS
// ========================

@Serializable
data class AuthRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val expiresIn: Long
)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class ChatRoomDto(
    val id: String,
    val name: String,
    val type: String,
    val participants: List<String>,
    val lastMessage: MessageDto?,
    val unreadCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val avatarUrl: String? = null
)

@Serializable
data class MessageDto(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val content: MessageContentDto,
    val timestamp: String,
    val status: String,
    val editedAt: String? = null,
    val replyTo: String? = null
)

@Serializable
data class MessageContentDto(
    val type: String,
    val text: String? = null,
    val url: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)

@Serializable
data class SendMessageRequest(
    val roomId: String,
    val content: MessageContentDto,
    val replyToId: String? = null
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val status: String,
    val lastSeen: String?
)

@Serializable
data class ErrorResponse(
    val error: String, 
    val message: String,
    val details: Map<String, String>? = null
)

// Client -> Server WebSocket Messages
@Serializable
sealed class ClientWebSocketMessage {
    @Serializable
    data class Authenticate(val userId: String) : ClientWebSocketMessage()
    
    @Serializable
    data class JoinRoom(val roomId: String) : ClientWebSocketMessage()
    
    @Serializable
    data class SendMessage(
        val messageId: String,
        val roomId: String,
        val content: MessageContentDto
    ) : ClientWebSocketMessage()
    
    @Serializable
    data class TypingIndicator(
        val roomId: String,
        val isTyping: Boolean
    ) : ClientWebSocketMessage()
}

// ========================
// SERVICES
// ========================

class AuthService(
    private val authRepository: AuthRepository,
    private val jwtConfig: AppConfig.JwtConfig
) {
    fun generateToken(userId: String, username: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + jwtConfig.accessTokenExpiry))
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}

class MessageService(
    private val messageRepository: MessageRepository,
    private val roomRepository: RoomRepository,
    private val webSocketManager: WebSocketManager
) {
    suspend fun sendMessage(
        senderId: String,
        request: SendMessageRequest
    ): MessageDto? {
        // Verify user has access to room
        if (!roomRepository.userHasAccessToRoom(senderId, request.roomId)) {
            throw UnauthorizedException("User does not have access to this room")
        }
        
        // Save message
        val message = messageRepository.sendMessage(
            roomId = request.roomId,
            senderId = senderId,
            contentType = request.content.type,
            contentText = request.content.text,
            contentUrl = request.content.url,
            fileName = request.content.fileName,
            fileSize = request.content.fileSize,
            replyToId = request.replyToId
        )
        
        // Broadcast to WebSocket connections
        message?.let {
            webSocketManager.broadcastToRoom(
                roomId = request.roomId,
                message = WebSocketMessage.NewMessage(it),
                excludeUserId = senderId
            )
        }
        
        return message
    }
    
    suspend fun getMessages(roomId: String, limit: Int): List<MessageDto> {
        return messageRepository.getMessages(roomId, limit)
    }
}

// ========================
// EXCEPTIONS
// ========================

class ValidationException(message: String, val errors: Map<String, String>) : Exception(message)
class UnauthorizedException(message: String) : Exception(message)
class NotFoundException(message: String) : Exception(message)

// ========================
// APPLICATION MODULE
// ========================

fun Application.module(config: AppConfig.Config) {
    // Initialize repositories
    val authRepository = AuthRepository()
    val userRepository = UserRepository()
    val messageRepository = MessageRepository()
    val roomRepository = RoomRepository(messageRepository)
    val webSocketManager = WebSocketManager()
    
    // Initialize services
    val authService = AuthService(authRepository, config.jwt)
    val messageService = MessageService(messageRepository, roomRepository, webSocketManager)
    
    // Install features
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                error = "VALIDATION_ERROR",
                message = cause.message ?: "Validation failed",
                details = cause.errors
            ))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                error = "UNAUTHORIZED",
                message = cause.message ?: "Unauthorized"
            ))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(
                error = "NOT_FOUND",
                message = cause.message ?: "Resource not found"
            ))
        }
        exception<Exception> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                error = "INTERNAL_ERROR",
                message = "An unexpected error occurred"
            ))
        }
    }

    // JWT Authentication
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.jwt.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.jwt.secret))
                    .withAudience(config.jwt.audience)
                    .withIssuer(config.jwt.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(config.jwt.audience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                    error = "TOKEN_INVALID",
                    message = "Invalid or expired token"
                ))
            }
        }
    }

    // Routes
    routing {
        healthRoutes()
        authRoutes(authRepository, authService, userRepository)
        
        authenticate("auth-jwt") {
            roomRoutes(roomRepository)
            messageRoutes(messageService, roomRepository)
            userRoutes(userRepository)
        }
        
        webSocketRoute(webSocketManager, userRepository, messageService)
    }
}

// ========================
// ROUTE HANDLERS
// ========================

fun Route.healthRoutes() {
    get("/") {
        call.respondText("🚀 Chatty Backend Server is running!")
    }

    get("/health") {
        call.respond(mapOf(
            "status" to "healthy",
            "service" to "chatty-backend",
            "timestamp" to Clock.System.now().toString()
        ))
    }
}

fun Route.authRoutes(
    authRepository: AuthRepository,
    authService: AuthService,
    userRepository: UserRepository
) {
    route("/auth") {
        post("/login") {
            val request = call.receive<AuthRequest>()
            
            val user = authRepository.authenticate(request.username, request.password)
                ?: throw UnauthorizedException("Invalid username or password")
            
            val token = authService.generateToken(user.id, user.username)
            val refreshToken = java.util.UUID.randomUUID().toString()
            
            call.respond(HttpStatusCode.OK, AuthResponse(
                token = token,
                refreshToken = refreshToken,
                userId = user.id,
                username = user.username,
                displayName = user.displayName,
                expiresIn = 3600000
            ))
        }
        
        post("/register") {
            val request = call.receive<RegisterRequest>()
            
            // Validate input
            val errors = mutableMapOf<String, String>()
            
            when (val result = Validator.validateUsername(request.username)) {
                is Validator.ValidationResult.Error -> errors["username"] = result.message
                else -> {}
            }
            
            when (val result = Validator.validatePassword(request.password)) {
                is Validator.ValidationResult.Error -> errors["password"] = result.message
                else -> {}
            }
            
            when (val result = Validator.validateDisplayName(request.displayName)) {
                is Validator.ValidationResult.Error -> errors["displayName"] = result.message
                else -> {}
            }
            
            if (errors.isNotEmpty()) {
                throw ValidationException("Validation failed", errors)
            }
            
            // Check if username already exists
            if (userRepository.getUserByUsername(request.username) != null) {
                throw ValidationException(
                    "Registration failed",
                    mapOf("username" to "Username already exists")
                )
            }
            
            val user = authRepository.register(request)
                ?: throw Exception("Registration failed")
            
            val token = authService.generateToken(user.userId, user.username)
            val refreshToken = java.util.UUID.randomUUID().toString()
            
            call.respond(HttpStatusCode.Created, AuthResponse(
                token = token,
                refreshToken = refreshToken,
                userId = user.userId,
                username = user.username,
                displayName = user.displayName,
                expiresIn = 3600000
            ))
        }
        
        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            
            // Mock refresh for now
            val token = authService.generateToken("user-1", "alice")
            
            call.respond(HttpStatusCode.OK, AuthResponse(
                token = token,
                refreshToken = request.refreshToken,
                userId = "user-1",
                username = "alice",
                displayName = "Alice Johnson",
                expiresIn = 3600000
            ))
        }
    }
}

fun Route.roomRoutes(roomRepository: RoomRepository) {
    route("/rooms") {
        get {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("userId").asString()
            
            val rooms = roomRepository.getRoomsForUser(userId)
            call.respond(HttpStatusCode.OK, rooms)
        }
        
        get("/{id}") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("userId").asString()
            val roomId = call.parameters["id"] ?: throw ValidationException(
                "Missing room ID",
                mapOf("id" to "Room ID is required")
            )
            
            // Verify user has access
            if (!roomRepository.userHasAccessToRoom(userId, roomId)) {
                throw UnauthorizedException("Access denied to this room")
            }
            
            val room = roomRepository.getRoomById(roomId)
                ?: throw NotFoundException("Room not found")
            
            call.respond(HttpStatusCode.OK, room)
        }
    }
}

fun Route.messageRoutes(
    messageService: MessageService,
    roomRepository: RoomRepository
) {
    route("/messages") {
        get {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("userId").asString()
            
            val roomId = call.request.queryParameters["roomId"]
                ?: throw ValidationException(
                    "Missing room ID",
                    mapOf("roomId" to "Room ID is required")
                )
            
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            
            // Verify user has access
            if (!roomRepository.userHasAccessToRoom(userId, roomId)) {
                throw UnauthorizedException("Access denied to this room")
            }
            
            val messages = messageService.getMessages(roomId, limit)
            call.respond(HttpStatusCode.OK, messages)
        }
        
        post {
            val principal = call.principal<JWTPrincipal>()!!
            val senderId = principal.payload.getClaim("userId").asString()
            
            val request = call.receive<SendMessageRequest>()
            
            val message = messageService.sendMessage(senderId, request)
                ?: throw Exception("Failed to send message")
            
            call.respond(HttpStatusCode.Created, message)
        }
    }
}

fun Route.userRoutes(userRepository: UserRepository) {
    route("/users") {
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

fun Route.webSocketRoute(
    webSocketManager: WebSocketManager,
    userRepository: UserRepository,
    messageService: MessageService
) {
    webSocket("/ws") {
        var currentUserId: String? = null
        val sessionId = java.util.UUID.randomUUID().toString()
        
        try {
            // Send connection confirmation
            val connectedMsg = WebSocketMessage.Connected(
                userId = "guest",
                timestamp = Clock.System.now().toString()
            )
            outgoing.send(Frame.Text(Json.encodeToString(WebSocketMessage.serializer(), connectedMsg)))
            
            application.log.info("New WebSocket connection: $sessionId")
            
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val text = String(frame.data)
                            val message = Json.decodeFromString<ClientWebSocketMessage>(text)
                            
                            when (message) {
                                is ClientWebSocketMessage.Authenticate -> {
                                    currentUserId = message.userId
                                    webSocketManager.addConnection(message.userId, this)
                                    application.log.info("User ${message.userId} authenticated")
                                    
                                    val authSuccess = WebSocketMessage.AuthenticationSuccess(
                                        userId = message.userId,
                                        timestamp = Clock.System.now().toString()
                                    )
                                    outgoing.send(Frame.Text(Json.encodeToString(WebSocketMessage.serializer(), authSuccess)))
                                }
                                
                                is ClientWebSocketMessage.JoinRoom -> {
                                    currentUserId?.let { userId ->
                                        webSocketManager.joinRoom(userId, message.roomId)
                                    }
                                }
                                
                                is ClientWebSocketMessage.SendMessage -> {
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
                                            outgoing.send(Frame.Text(Json.encodeToString(
                                                WebSocketMessage.serializer(),
                                                WebSocketMessage.Error("Failed: ${e.message}")
                                            )))
                                        }
                                    }
                                }
                                
                                is ClientWebSocketMessage.TypingIndicator -> {
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
