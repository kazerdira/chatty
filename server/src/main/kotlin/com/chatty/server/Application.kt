package com.chatty.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// ========================
// DATA MODELS (Mock)
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
    val expiresIn: Long = 3600000 // 1 hour
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
    val imageUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val status: String,
    val lastSeen: String
)

@Serializable
data class ErrorResponse(val error: String, val message: String)

// ========================
// IN-MEMORY STORAGE (Mock Database)
// ========================

object MockDatabase {
    private val users = ConcurrentHashMap<String, UserDto>()
    private val rooms = ConcurrentHashMap<String, ChatRoomDto>()
    private val messages = ConcurrentHashMap<String, MutableList<MessageDto>>()
    private val credentials = ConcurrentHashMap<String, String>() // username -> password
    
    init {
        // Create mock users
        val user1 = UserDto(
            id = "user-1",
            username = "alice",
            displayName = "Alice Johnson",
            avatarUrl = null,
            status = "ONLINE",
            lastSeen = Clock.System.now().toString()
        )
        val user2 = UserDto(
            id = "user-2",
            username = "bob",
            displayName = "Bob Smith",
            avatarUrl = null,
            status = "ONLINE",
            lastSeen = Clock.System.now().toString()
        )
        
        users["user-1"] = user1
        users["user-2"] = user2
        credentials["alice"] = "password123"
        credentials["bob"] = "password123"
        
        // Create mock rooms
        val room1 = ChatRoomDto(
            id = "room-1",
            name = "General Chat",
            type = "GROUP",
            participants = listOf("user-1", "user-2"),
            lastMessage = MessageDto(
                id = "msg-1",
                roomId = "room-1",
                senderId = "user-2",
                senderName = "Bob Smith",
                content = MessageContentDto(type = "TEXT", text = "Hey, how are you?"),
                timestamp = Clock.System.now().toString(),
                status = "DELIVERED"
            ),
            unreadCount = 1,
            createdAt = Clock.System.now().toString(),
            updatedAt = Clock.System.now().toString()
        )
        
        rooms["room-1"] = room1
        messages["room-1"] = mutableListOf(
            room1.lastMessage!!,
            MessageDto(
                id = "msg-2",
                roomId = "room-1",
                senderId = "user-1",
                senderName = "Alice Johnson",
                content = MessageContentDto(type = "TEXT", text = "I'm doing great! Thanks for asking 😊"),
                timestamp = Clock.System.now().toString(),
                status = "DELIVERED"
            )
        )
    }
    
    fun authenticate(username: String, password: String): UserDto? {
        if (credentials[username] == password) {
            return users.values.find { it.username == username }
        }
        return null
    }
    
    fun register(username: String, password: String, displayName: String): UserDto {
        val userId = "user-${UUID.randomUUID()}"
        val user = UserDto(
            id = userId,
            username = username,
            displayName = displayName,
            avatarUrl = null,
            status = "ONLINE",
            lastSeen = Clock.System.now().toString()
        )
        users[userId] = user
        credentials[username] = password
        return user
    }
    
    fun getUser(userId: String) = users[userId]
    
    fun getRooms() = rooms.values.toList()
    
    fun getRoom(roomId: String) = rooms[roomId]
    
    fun getMessages(roomId: String, limit: Int = 50) = messages[roomId]?.takeLast(limit) ?: emptyList()
    
    fun addMessage(message: MessageDto) {
        messages.getOrPut(message.roomId) { mutableListOf() }.add(message)
    }
    
    fun searchUsers(query: String) = users.values.filter {
        it.username.contains(query, ignoreCase = true) ||
        it.displayName.contains(query, ignoreCase = true)
    }
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
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

    // JWT Configuration
    val jwtSecret = System.getenv("JWT_SECRET") ?: "chatty-super-secret-key-change-in-production"
    val jwtIssuer = "chatty-server"
    val jwtAudience = "chatty-users"
    val jwtRealm = "Chatty App"

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    routing {
        // ========================
        // PUBLIC ENDPOINTS
        // ========================
        
        get("/") {
            call.respondText("🚀 Chatty Backend Server is running!")
        }

        get("/health") {
            call.respond(mapOf("status" to "healthy", "service" to "chatty-backend", "timestamp" to Clock.System.now().toString()))
        }

        // ========================
        // AUTH ENDPOINTS
        // ========================
        
        route("/auth") {
            post("/login") {
                try {
                    val request = call.receive<AuthRequest>()
                    val user = MockDatabase.authenticate(request.username, request.password)
                    
                    if (user != null) {
                        val token = JWT.create()
                            .withAudience(jwtAudience)
                            .withIssuer(jwtIssuer)
                            .withClaim("userId", user.id)
                            .withClaim("username", user.username)
                            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                            .sign(Algorithm.HMAC256(jwtSecret))
                        
                        val refreshToken = UUID.randomUUID().toString()
                        
                        call.respond(HttpStatusCode.OK, AuthResponse(
                            token = token,
                            refreshToken = refreshToken,
                            userId = user.id,
                            username = user.username,
                            displayName = user.displayName
                        ))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                            error = "INVALID_CREDENTIALS",
                            message = "Invalid username or password"
                        ))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "BAD_REQUEST",
                        message = e.message ?: "Invalid request"
                    ))
                }
            }
            
            post("/register") {
                try {
                    val request = call.receive<RegisterRequest>()
                    val user = MockDatabase.register(request.username, request.password, request.displayName)
                    
                    val token = JWT.create()
                        .withAudience(jwtAudience)
                        .withIssuer(jwtIssuer)
                        .withClaim("userId", user.id)
                        .withClaim("username", user.username)
                        .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                        .sign(Algorithm.HMAC256(jwtSecret))
                    
                    val refreshToken = UUID.randomUUID().toString()
                    
                    call.respond(HttpStatusCode.Created, AuthResponse(
                        token = token,
                        refreshToken = refreshToken,
                        userId = user.id,
                        username = user.username,
                        displayName = user.displayName
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "BAD_REQUEST",
                        message = e.message ?: "Invalid request"
                    ))
                }
            }
            
            post("/refresh") {
                try {
                    val request = call.receive<RefreshTokenRequest>()
                    // In a real app, validate the refresh token
                    
                    val token = JWT.create()
                        .withAudience(jwtAudience)
                        .withIssuer(jwtIssuer)
                        .withClaim("userId", "user-1") // Mock
                        .withClaim("username", "alice") // Mock
                        .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                        .sign(Algorithm.HMAC256(jwtSecret))
                    
                    call.respond(HttpStatusCode.OK, AuthResponse(
                        token = token,
                        refreshToken = request.refreshToken,
                        userId = "user-1",
                        username = "alice",
                        displayName = "Alice Johnson"
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "BAD_REQUEST",
                        message = e.message ?: "Invalid request"
                    ))
                }
            }
        }

        // ========================
        // PROTECTED ENDPOINTS
        // ========================
        
        authenticate("auth-jwt") {
            // Rooms
            route("/rooms") {
                get {
                    call.respond(HttpStatusCode.OK, MockDatabase.getRooms())
                }
                
                get("/{id}") {
                    val roomId = call.parameters["id"]
                    val room = MockDatabase.getRoom(roomId ?: "")
                    
                    if (room != null) {
                        call.respond(HttpStatusCode.OK, room)
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse(
                            error = "NOT_FOUND",
                            message = "Room not found"
                        ))
                    }
                }
            }
            
            // Messages
            route("/messages") {
                get {
                    val roomId = call.request.queryParameters["roomId"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    
                    if (roomId != null) {
                        call.respond(HttpStatusCode.OK, MockDatabase.getMessages(roomId, limit))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            error = "BAD_REQUEST",
                            message = "roomId parameter is required"
                        ))
                    }
                }
                
                post {
                    try {
                        val message = call.receive<MessageDto>()
                        MockDatabase.addMessage(message)
                        call.respond(HttpStatusCode.Created, message)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            error = "BAD_REQUEST",
                            message = e.message ?: "Invalid request"
                        ))
                    }
                }
            }
            
            // Users
            route("/users") {
                get("/search") {
                    val query = call.request.queryParameters["q"] ?: ""
                    call.respond(HttpStatusCode.OK, MockDatabase.searchUsers(query))
                }
                
                get("/{id}") {
                    val userId = call.parameters["id"]
                    val user = MockDatabase.getUser(userId ?: "")
                    
                    if (user != null) {
                        call.respond(HttpStatusCode.OK, user)
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse(
                            error = "NOT_FOUND",
                            message = "User not found"
                        ))
                    }
                }
            }
        }
        
        // WebSocket endpoint (for future real-time messaging)
        webSocket("/ws") {
            try {
                outgoing.send(io.ktor.websocket.Frame.Text("Connected to Chatty WebSocket"))
                for (frame in incoming) {
                    // Handle incoming messages (echo for now)
                    when (frame) {
                        is io.ktor.websocket.Frame.Text -> {
                            val text = String(frame.data)
                            outgoing.send(io.ktor.websocket.Frame.Text("Echo: $text"))
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.message}")
            }
        }
    }
}
