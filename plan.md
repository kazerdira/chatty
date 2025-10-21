# Building a Real-Time Chat Application with Ktor & Kotlin Compose Multiplatform

## Table of Contents
1. [Overview & Architecture](#overview--architecture)
2. [Project Setup & Structure](#project-setup--structure)
3. [Domain Layer](#domain-layer)
4. [Backend Development with Ktor](#backend-development-with-ktor)
5. [Client-Side Architecture](#client-side-architecture)
6. [UI Implementation with Compose](#ui-implementation-with-compose)
7. [Real-Time Features](#real-time-features)
8. [Authentication & Security](#authentication--security)
9. [Media Handling](#media-handling)
10. [Testing Strategy](#testing-strategy)
11. [Performance Optimization](#performance-optimization)
12. [Deployment & DevOps](#deployment--devops)

## Overview & Architecture

### Clean Architecture Principles

The application follows Clean Architecture principles with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│  (Compose UI, ViewModels, Platform-specific UI components)  │
├─────────────────────────────────────────────────────────────┤
│                       Domain Layer                           │
│     (Use Cases, Entity Models, Repository Interfaces)       │
├─────────────────────────────────────────────────────────────┤
│                        Data Layer                            │
│  (Repository Implementations, Network, Local Database, DTOs) │
├─────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                      │
│   (DI Container, Platform APIs, External Service Adapters)  │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack

- **Backend**: Ktor Server with WebSockets
- **Frontend**: Kotlin Compose Multiplatform
- **Database**: PostgreSQL with Exposed ORM (Server), SQLDelight (Client)
- **Authentication**: JWT with refresh tokens
- **Real-time**: WebSockets for bidirectional communication
- **DI**: Koin for dependency injection
- **Testing**: Kotest, MockK, Compose Testing

## Project Setup & Structure

### Module Structure

```
chat-app/
├── buildSrc/
│   └── src/main/kotlin/
│       ├── Dependencies.kt
│       └── Versions.kt
├── shared/
│   ├── commonMain/
│   │   ├── kotlin/
│   │   │   ├── domain/
│   │   │   ├── data/
│   │   │   └── presentation/
│   │   └── resources/
│   ├── androidMain/
│   ├── iosMain/
│   └── desktopMain/
├── server/
│   └── src/
│       ├── main/kotlin/
│       │   ├── routes/
│       │   ├── services/
│       │   ├── data/
│       │   └── Application.kt
│       └── resources/
├── androidApp/
├── iosApp/
└── desktopApp/
```

### Root build.gradle.kts

```kotlin
plugins {
    kotlin("multiplatform") version "1.9.22" apply false
    kotlin("android") version "1.9.22" apply false
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.compose") version "1.5.12" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

### Shared Module Configuration

```kotlin
kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.animation)
                
                // Ktor Client
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-websockets:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                
                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                
                // Koin DI
                implementation("io.insert-koin:koin-core:3.5.3")
                implementation("io.insert-koin:koin-compose:1.1.2")
                
                // SQLDelight
                implementation("app.cash.sqldelight:runtime:2.0.1")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-android:2.3.7")
                implementation("app.cash.sqldelight:android-driver:2.0.1")
            }
        }
        
        val iosMain by creating {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.7")
                implementation("app.cash.sqldelight:native-driver:2.0.1")
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:2.3.7")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
            }
        }
    }
}
```

## Domain Layer

### Entity Models

```kotlin
// User.kt
data class User(
    val id: UserId,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val status: UserStatus,
    val lastSeen: Instant,
    val createdAt: Instant
) {
    @JvmInline
    value class UserId(val value: String)
    
    enum class UserStatus {
        ONLINE, AWAY, OFFLINE
    }
}

// Message.kt
data class Message(
    val id: MessageId,
    val roomId: ChatRoom.RoomId,
    val senderId: User.UserId,
    val content: MessageContent,
    val timestamp: Instant,
    val status: MessageStatus,
    val editedAt: Instant?,
    val replyTo: MessageId?
) {
    @JvmInline
    value class MessageId(val value: String)
    
    sealed class MessageContent {
        data class Text(val text: String) : MessageContent()
        data class Image(val url: String, val thumbnailUrl: String) : MessageContent()
        data class Video(val url: String, val thumbnailUrl: String, val duration: Duration) : MessageContent()
        data class File(val url: String, val fileName: String, val size: Long) : MessageContent()
        data class Voice(val url: String, val duration: Duration) : MessageContent()
    }
    
    enum class MessageStatus {
        SENDING, SENT, DELIVERED, READ, FAILED
    }
}

// ChatRoom.kt
data class ChatRoom(
    val id: RoomId,
    val name: String,
    val type: RoomType,
    val participants: List<User.UserId>,
    val lastMessage: Message?,
    val unreadCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    @JvmInline
    value class RoomId(val value: String)
    
    enum class RoomType {
        DIRECT, GROUP, CHANNEL
    }
}

// TypingIndicator.kt
data class TypingIndicator(
    val roomId: ChatRoom.RoomId,
    val userId: User.UserId,
    val timestamp: Instant
)
```

### Use Cases

```kotlin
// SendMessageUseCase.kt
class SendMessageUseCase(
    private val messageRepository: MessageRepository,
    private val roomRepository: ChatRoomRepository
) {
    suspend operator fun invoke(params: SendMessageParams): Result<Message> {
        return runCatching {
            // Validate user is participant
            val room = roomRepository.getRoom(params.roomId)
                ?: throw IllegalArgumentException("Room not found")
            
            require(room.participants.contains(params.senderId)) {
                "User is not a participant of this room"
            }
            
            // Create message
            val message = Message(
                id = Message.MessageId(generateId()),
                roomId = params.roomId,
                senderId = params.senderId,
                content = params.content,
                timestamp = Clock.System.now(),
                status = Message.MessageStatus.SENDING,
                editedAt = null,
                replyTo = params.replyTo
            )
            
            // Send message
            messageRepository.sendMessage(message)
        }
    }
    
    data class SendMessageParams(
        val roomId: ChatRoom.RoomId,
        val senderId: User.UserId,
        val content: Message.MessageContent,
        val replyTo: Message.MessageId? = null
    )
}

// ObserveMessagesUseCase.kt
class ObserveMessagesUseCase(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(roomId: ChatRoom.RoomId): Flow<List<Message>> {
        return messageRepository.observeMessages(roomId)
    }
}

// JoinRoomUseCase.kt
class JoinRoomUseCase(
    private val roomRepository: ChatRoomRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(params: JoinRoomParams): Result<ChatRoom> {
        return runCatching {
            val user = userRepository.getCurrentUser()
                ?: throw IllegalStateException("User not authenticated")
            
            roomRepository.joinRoom(params.roomId, user.id)
        }
    }
    
    data class JoinRoomParams(
        val roomId: ChatRoom.RoomId
    )
}
```

### Repository Interfaces

```kotlin
// MessageRepository.kt
interface MessageRepository {
    suspend fun sendMessage(message: Message): Message
    suspend fun getMessage(messageId: Message.MessageId): Message?
    suspend fun getMessages(roomId: ChatRoom.RoomId, limit: Int = 50): List<Message>
    fun observeMessages(roomId: ChatRoom.RoomId): Flow<List<Message>>
    suspend fun markAsRead(messageIds: List<Message.MessageId>)
    suspend fun deleteMessage(messageId: Message.MessageId)
    suspend fun editMessage(messageId: Message.MessageId, newContent: Message.MessageContent): Message
}

// ChatRoomRepository.kt
interface ChatRoomRepository {
    suspend fun createRoom(name: String, type: ChatRoom.RoomType, participantIds: List<User.UserId>): ChatRoom
    suspend fun getRoom(roomId: ChatRoom.RoomId): ChatRoom?
    suspend fun getRooms(): List<ChatRoom>
    fun observeRooms(): Flow<List<ChatRoom>>
    suspend fun joinRoom(roomId: ChatRoom.RoomId, userId: User.UserId): ChatRoom
    suspend fun leaveRoom(roomId: ChatRoom.RoomId, userId: User.UserId)
    suspend fun updateRoom(roomId: ChatRoom.RoomId, name: String? = null): ChatRoom
}

// UserRepository.kt
interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun getUser(userId: User.UserId): User?
    suspend fun getUsers(userIds: List<User.UserId>): List<User>
    suspend fun searchUsers(query: String): List<User>
    suspend fun updateUserStatus(status: User.UserStatus)
    fun observeUserStatus(userId: User.UserId): Flow<User.UserStatus>
}
```

## Backend Development with Ktor

### Application Configuration

```kotlin
// Application.kt
fun main() {
    embeddedServer(Netty, port = 8080) {
        configureSerialization()
        configureSecurity()
        configureSockets()
        configureRouting()
        configureDatabase()
        configureDI()
        configureMonitoring()
    }.start(wait = true)
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "chat-app"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
    
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAge = 24.hours
            cookie.secure = true
            cookie.httpOnly = true
        }
    }
}

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
```

### WebSocket Session Manager

```kotlin
// WebSocketSessionManager.kt
class WebSocketSessionManager {
    private val sessions = ConcurrentHashMap<User.UserId, MutableSet<WebSocketSession>>()
    private val roomSessions = ConcurrentHashMap<ChatRoom.RoomId, MutableSet<User.UserId>>()
    
    fun addSession(userId: User.UserId, session: WebSocketSession) {
        sessions.getOrPut(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }
    
    fun removeSession(userId: User.UserId, session: WebSocketSession) {
        sessions[userId]?.remove(session)
        if (sessions[userId]?.isEmpty() == true) {
            sessions.remove(userId)
            // Update user status to offline
            updateUserStatus(userId, User.UserStatus.OFFLINE)
        }
    }
    
    fun joinRoom(userId: User.UserId, roomId: ChatRoom.RoomId) {
        roomSessions.getOrPut(roomId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }
    
    fun leaveRoom(userId: User.UserId, roomId: ChatRoom.RoomId) {
        roomSessions[roomId]?.remove(userId)
    }
    
    suspend fun sendToUser(userId: User.UserId, message: WebSocketMessage) {
        sessions[userId]?.forEach { session ->
            try {
                session.send(Json.encodeToString(WebSocketMessage.serializer(), message))
            } catch (e: Exception) {
                // Handle closed session
                removeSession(userId, session)
            }
        }
    }
    
    suspend fun broadcastToRoom(roomId: ChatRoom.RoomId, message: WebSocketMessage, excludeUser: User.UserId? = null) {
        roomSessions[roomId]?.forEach { userId ->
            if (userId != excludeUser) {
                sendToUser(userId, message)
            }
        }
    }
}
```

### WebSocket Routes

```kotlin
// WebSocketRoutes.kt
fun Route.webSocketRoutes(sessionManager: WebSocketSessionManager, messageService: MessageService) {
    authenticate("auth-jwt") {
        webSocket("/ws") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()
                ?.let { User.UserId(it) }
                ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No userId"))
            
            sessionManager.addSession(userId, this)
            
            try {
                // Send initial connection success
                send(Json.encodeToString(
                    WebSocketMessage.Connected(
                        userId = userId.value,
                        timestamp = Clock.System.now()
                    )
                ))
                
                // Handle incoming messages
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            handleWebSocketMessage(
                                userId = userId,
                                message = frame.readText(),
                                sessionManager = sessionManager,
                                messageService = messageService
                            )
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                log.error("WebSocket error for user $userId", e)
            } finally {
                sessionManager.removeSession(userId, this)
            }
        }
    }
}

suspend fun handleWebSocketMessage(
    userId: User.UserId,
    message: String,
    sessionManager: WebSocketSessionManager,
    messageService: MessageService
) {
    val wsMessage = Json.decodeFromString<WebSocketMessage>(message)
    
    when (wsMessage) {
        is WebSocketMessage.SendMessage -> {
            val sentMessage = messageService.sendMessage(
                senderId = userId,
                roomId = ChatRoom.RoomId(wsMessage.roomId),
                content = wsMessage.content.toMessageContent()
            )
            
            // Broadcast to room
            sessionManager.broadcastToRoom(
                roomId = ChatRoom.RoomId(wsMessage.roomId),
                message = WebSocketMessage.MessageReceived(
                    message = sentMessage.toDto()
                )
            )
        }
        
        is WebSocketMessage.TypingIndicator -> {
            sessionManager.broadcastToRoom(
                roomId = ChatRoom.RoomId(wsMessage.roomId),
                message = WebSocketMessage.UserTyping(
                    userId = userId.value,
                    roomId = wsMessage.roomId,
                    isTyping = wsMessage.isTyping
                ),
                excludeUser = userId
            )
        }
        
        is WebSocketMessage.MarkAsRead -> {
            messageService.markMessagesAsRead(
                messageIds = wsMessage.messageIds.map { Message.MessageId(it) },
                userId = userId
            )
        }
        
        is WebSocketMessage.JoinRoom -> {
            sessionManager.joinRoom(userId, ChatRoom.RoomId(wsMessage.roomId))
        }
        
        is WebSocketMessage.LeaveRoom -> {
            sessionManager.leaveRoom(userId, ChatRoom.RoomId(wsMessage.roomId))
        }
    }
}
```

### Database Layer with Exposed

```kotlin
// DatabaseTables.kt
object Users : Table() {
    val id = varchar("id", 36).primaryKey()
    val username = varchar("username", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val passwordHash = varchar("password_hash", 100)
    val status = enumeration("status", User.UserStatus::class).default(User.UserStatus.OFFLINE)
    val lastSeen = timestamp("last_seen")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}

object ChatRooms : Table() {
    val id = varchar("id", 36).primaryKey()
    val name = varchar("name", 100)
    val type = enumeration("type", ChatRoom.RoomType::class)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

object RoomParticipants : Table() {
    val roomId = reference("room_id", ChatRooms.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp())
    val role = enumeration("role", ParticipantRole::class).default(ParticipantRole.MEMBER)
    
    override val primaryKey = PrimaryKey(roomId, userId)
    
    enum class ParticipantRole {
        ADMIN, MODERATOR, MEMBER
    }
}

object Messages : Table() {
    val id = varchar("id", 36).primaryKey()
    val roomId = reference("room_id", ChatRooms.id, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val contentType = enumeration("content_type", MessageContentType::class)
    val content = text("content")
    val timestamp = timestamp("timestamp").defaultExpression(CurrentTimestamp())
    val editedAt = timestamp("edited_at").nullable()
    val replyToId = reference("reply_to_id", id).nullable()
    val isDeleted = bool("is_deleted").default(false)
    
    init {
        index(false, roomId, timestamp)
    }
}

object MessageStatus : Table() {
    val messageId = reference("message_id", Messages.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val status = enumeration("status", Message.MessageStatus::class)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
    
    override val primaryKey = PrimaryKey(messageId, userId)
}

// DatabaseFactory.kt
object DatabaseFactory {
    fun init(config: DatabaseConfig) {
        val database = Database.connect(
            url = config.jdbcUrl,
            driver = config.driver,
            user = config.user,
            password = config.password
        )
        
        transaction(database) {
            addLogger(StdOutSqlLogger)
            
            SchemaUtils.create(
                Users,
                ChatRooms,
                RoomParticipants,
                Messages,
                MessageStatus,
                Attachments,
                PushTokens
            )
        }
    }
}
```

## Client-Side Architecture

### Network Layer

```kotlin
// ChatApiClient.kt
class ChatApiClient(
    private val httpClient: HttpClient,
    private val websocketClient: HttpClient,
    private val tokenManager: TokenManager
) {
    private val baseUrl = "https://api.chatapp.com"
    private var websocketSession: WebSocketSession? = null
    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>()
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()
    
    suspend fun connectWebSocket() {
        try {
            websocketSession = websocketClient.webSocketSession(
                urlString = "$baseUrl/ws"
            ) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${tokenManager.getAccessToken()}")
                }
            }
            
            // Handle incoming messages
            websocketSession?.incoming?.receiveAsFlow()?.collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val message = Json.decodeFromString<WebSocketMessage>(frame.readText())
                        _incomingMessages.emit(message)
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            // Handle reconnection
            reconnectWithBackoff()
        }
    }
    
    private suspend fun reconnectWithBackoff() {
        var backoffDelay = 1000L // Start with 1 second
        val maxDelay = 32000L // Max 32 seconds
        
        while (true) {
            delay(backoffDelay)
            
            try {
                connectWebSocket()
                break // Successfully reconnected
            } catch (e: Exception) {
                backoffDelay = minOf(backoffDelay * 2, maxDelay)
            }
        }
    }
    
    suspend fun sendMessage(message: WebSocketMessage): Result<Unit> {
        return runCatching {
            websocketSession?.send(
                Frame.Text(Json.encodeToString(WebSocketMessage.serializer(), message))
            ) ?: throw IllegalStateException("WebSocket not connected")
        }
    }
    
    suspend fun getRooms(): Result<List<ChatRoomDto>> {
        return safeApiCall {
            httpClient.get("$baseUrl/rooms") {
                bearerAuth(tokenManager.getAccessToken())
            }.body()
        }
    }
    
    suspend fun getMessages(roomId: String, before: Instant? = null, limit: Int = 50): Result<List<MessageDto>> {
        return safeApiCall {
            httpClient.get("$baseUrl/rooms/$roomId/messages") {
                bearerAuth(tokenManager.getAccessToken())
                parameter("limit", limit)
                before?.let { parameter("before", it.toString()) }
            }.body()
        }
    }
}

// NetworkModule.kt
val networkModule = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            
            install(WebSockets)
            
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
    }
}
```

### Repository Implementations

```kotlin
// MessageRepositoryImpl.kt
class MessageRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val messageDao: MessageDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : MessageRepository {
    
    init {
        // Listen to WebSocket messages
        GlobalScope.launch {
            apiClient.incomingMessages.collect { wsMessage ->
                when (wsMessage) {
                    is WebSocketMessage.MessageReceived -> {
                        val message = wsMessage.message.toEntity()
                        messageDao.insertMessage(message.toLocal())
                    }
                    is WebSocketMessage.MessageStatusUpdate -> {
                        messageDao.updateMessageStatus(
                            messageId = wsMessage.messageId,
                            status = wsMessage.status
                        )
                    }
                }
            }
        }
    }
    
    override suspend fun sendMessage(message: Message): Message = withContext(dispatcher) {
        // Save to local DB with SENDING status
        messageDao.insertMessage(message.toLocal())
        
        // Send via WebSocket
        val result = apiClient.sendMessage(
            WebSocketMessage.SendMessage(
                roomId = message.roomId.value,
                content = message.content.toDto(),
                replyToId = message.replyTo?.value
            )
        )
        
        if (result.isFailure) {
            // Update status to FAILED
            messageDao.updateMessageStatus(message.id.value, Message.MessageStatus.FAILED)
            throw result.exceptionOrNull() ?: Exception("Failed to send message")
        }
        
        message
    }
    
    override suspend fun getMessage(messageId: Message.MessageId): Message? = withContext(dispatcher) {
        messageDao.getMessage(messageId.value)?.toEntity()
    }
    
    override suspend fun getMessages(roomId: ChatRoom.RoomId, limit: Int): List<Message> = withContext(dispatcher) {
        // Try to get from local first
        val localMessages = messageDao.getMessages(roomId.value, limit)
        
        if (localMessages.isEmpty()) {
            // Fetch from server
            apiClient.getMessages(roomId.value, limit = limit).fold(
                onSuccess = { messages ->
                    val entities = messages.map { it.toEntity() }
                    messageDao.insertMessages(entities.map { it.toLocal() })
                    entities
                },
                onFailure = { emptyList() }
            )
        } else {
            localMessages.map { it.toEntity() }
        }
    }
    
    override fun observeMessages(roomId: ChatRoom.RoomId): Flow<List<Message>> {
        return messageDao.observeMessages(roomId.value)
            .map { messages -> messages.map { it.toEntity() } }
            .flowOn(dispatcher)
    }
}

// ChatRoomRepositoryImpl.kt
class ChatRoomRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val roomDao: ChatRoomDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ChatRoomRepository {
    
    override suspend fun getRooms(): List<ChatRoom> = withContext(dispatcher) {
        // Sync with server
        apiClient.getRooms().fold(
            onSuccess = { rooms ->
                val entities = rooms.map { it.toEntity() }
                roomDao.insertRooms(entities.map { it.toLocal() })
                entities
            },
            onFailure = {
                // Fallback to local data
                roomDao.getRooms().map { it.toEntity() }
            }
        )
    }
    
    override fun observeRooms(): Flow<List<ChatRoom>> {
        return roomDao.observeRooms()
            .map { rooms -> rooms.map { it.toEntity() } }
            .flowOn(dispatcher)
    }
}
```

### Local Database with SQLDelight

```sql
-- ChatDatabase.sq

CREATE TABLE LocalUser (
    id TEXT PRIMARY KEY NOT NULL,
    username TEXT NOT NULL,
    displayName TEXT NOT NULL,
    avatarUrl TEXT,
    status TEXT NOT NULL,
    lastSeen INTEGER NOT NULL,
    createdAt INTEGER NOT NULL
);

CREATE TABLE LocalChatRoom (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    unreadCount INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);

CREATE TABLE LocalMessage (
    id TEXT PRIMARY KEY NOT NULL,
    roomId TEXT NOT NULL,
    senderId TEXT NOT NULL,
    contentType TEXT NOT NULL,
    contentData TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    status TEXT NOT NULL,
    editedAt INTEGER,
    replyToId TEXT,
    FOREIGN KEY (roomId) REFERENCES LocalChatRoom(id) ON DELETE CASCADE
);

CREATE INDEX idx_messages_room_timestamp ON LocalMessage(roomId, timestamp);

-- Queries
selectMessages:
SELECT * FROM LocalMessage
WHERE roomId = ?
ORDER BY timestamp DESC
LIMIT ?;

observeMessages:
SELECT * FROM LocalMessage
WHERE roomId = ?
ORDER BY timestamp ASC;

insertMessage:
INSERT OR REPLACE INTO LocalMessage
VALUES ?;

updateMessageStatus:
UPDATE LocalMessage
SET status = ?
WHERE id = ?;

selectRooms:
SELECT 
    r.*,
    m.id AS lastMessageId,
    m.contentType AS lastMessageType,
    m.contentData AS lastMessageData,
    m.timestamp AS lastMessageTime
FROM LocalChatRoom r
LEFT JOIN LocalMessage m ON m.id = (
    SELECT id FROM LocalMessage
    WHERE roomId = r.id
    ORDER BY timestamp DESC
    LIMIT 1
)
ORDER BY COALESCE(m.timestamp, r.updatedAt) DESC;

observeRooms:
SELECT 
    r.*,
    m.id AS lastMessageId,
    m.contentType AS lastMessageType,
    m.contentData AS lastMessageData,
    m.timestamp AS lastMessageTime
FROM LocalChatRoom r
LEFT JOIN LocalMessage m ON m.id = (
    SELECT id FROM LocalMessage
    WHERE roomId = r.id
    ORDER BY timestamp DESC
    LIMIT 1
)
ORDER BY COALESCE(m.timestamp, r.updatedAt) DESC;
```

## UI Implementation with Compose

### Navigation Setup

```kotlin
// Navigation.kt
@Composable
fun ChatNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated()) "chat_list" else "login"
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("chat_list") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        composable("chat_list") {
            ChatListScreen(
                onChatClick = { roomId ->
                    navController.navigate("chat_detail/${roomId.value}")
                },
                onCreateChat = {
                    navController.navigate("create_chat")
                }
            )
        }
        
        composable(
            "chat_detail/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            ChatDetailScreen(
                roomId = ChatRoom.RoomId(roomId),
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable("create_chat") {
            CreateChatScreen(
                onChatCreated = { roomId ->
                    navController.navigate("chat_detail/${roomId.value}") {
                        popUpTo("create_chat") { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
```

### Chat List Screen

```kotlin
// ChatListScreen.kt
@Composable
fun ChatListScreen(
    onChatClick: (ChatRoom.RoomId) -> Unit,
    onCreateChat: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    IconButton(onClick = onCreateChat) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ChatListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is ChatListUiState.Success -> {
                if (state.rooms.isEmpty()) {
                    EmptyState(
                        message = "No chats yet",
                        action = "Start a new chat",
                        onActionClick = onCreateChat
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        items(
                            items = state.rooms,
                            key = { it.id.value }
                        ) { room ->
                            ChatRoomItem(
                                room = room,
                                onClick = { onChatClick(room.id) }
                            )
                        }
                    }
                }
            }
            
            is ChatListUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = viewModel::refresh
                )
            }
        }
    }
}

@Composable
fun ChatRoomItem(
    room: ChatRoom,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            AsyncImage(
                model = room.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                placeholder = painterResource(Res.drawable.default_avatar),
                contentScale = ContentScale.Crop
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    room.lastMessage?.let { message ->
                        Text(
                            text = formatRelativeTime(message.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = room.lastMessage?.let { formatMessagePreview(it) } ?: "No messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (room.unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = room.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### Chat Detail Screen

```kotlin
// ChatDetailScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    roomId: ChatRoom.RoomId,
    onBackClick: () -> Unit,
    viewModel: ChatDetailViewModel = koinViewModel { parametersOf(roomId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.roomName)
                        uiState.typingUsers.takeIf { it.isNotEmpty() }?.let { users ->
                            Text(
                                text = formatTypingIndicator(users),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Show room info */ }) {
                        Icon(Icons.Default.Info, contentDescription = "Room Info")
                    }
                }
            )
        },
        bottomBar = {
            MessageInput(
                value = uiState.messageText,
                onValueChange = viewModel::updateMessageText,
                onSend = viewModel::sendMessage,
                isEnabled = uiState.canSendMessage,
                onTyping = viewModel::sendTypingIndicator
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id.value }
                ) { message ->
                    MessageItem(
                        message = message,
                        isOwnMessage = message.senderId == uiState.currentUserId,
                        onLongClick = { viewModel.showMessageOptions(message) }
                    )
                }
                
                // Load more indicator
                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            
            // Scroll to bottom FAB
            AnimatedVisibility(
                visible = listState.firstVisibleItemIndex > 5,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to bottom")
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    isOwnMessage: Boolean,
    onLongClick: () -> Unit
) {
    val alignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                bottomEnd = if (isOwnMessage) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                when (val content = message.content) {
                    is Message.MessageContent.Text -> {
                        Text(
                            text = content.text,
                            color = contentColor
                        )
                    }
                    
                    is Message.MessageContent.Image -> {
                        AsyncImage(
                            model = content.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    // Handle other content types...
                }
                
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    
                    if (isOwnMessage) {
                        MessageStatusIcon(
                            status = message.status,
                            tint = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    onTyping: () -> Unit
) {
    var lastTypingTime by remember { mutableStateOf(0L) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    
                    // Send typing indicator
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTypingTime > 3000) {
                        onTyping()
                        lastTypingTime = currentTime
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { /* Attach file */ }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                        }
                        IconButton(onClick = { /* Camera */ }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FilledIconButton(
                onClick = onSend,
                enabled = isEnabled && value.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
```

### ViewModels

```kotlin
// ChatListViewModel.kt
class ChatListViewModel(
    private val getRoomsUseCase: GetRoomsUseCase,
    private val observeRoomsUseCase: ObserveRoomsUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    init {
        loadRooms()
        observeRooms()
    }
    
    private fun loadRooms() {
        viewModelScope.launch {
            getRoomsUseCase().fold(
                onSuccess = { rooms ->
                    _uiState.value = ChatListUiState.Success(rooms)
                },
                onFailure = { error ->
                    _uiState.value = ChatListUiState.Error(
                        error.message ?: "Failed to load chats"
                    )
                }
            )
        }
    }
    
    private fun observeRooms() {
        observeRoomsUseCase()
            .onEach { rooms ->
                _uiState.value = ChatListUiState.Success(rooms)
            }
            .launchIn(viewModelScope)
    }
    
    fun refresh() {
        _uiState.value = ChatListUiState.Loading
        loadRooms()
    }
}

sealed class ChatListUiState {
    object Loading : ChatListUiState()
    data class Success(val rooms: List<ChatRoom>) : ChatListUiState()
    data class Error(val message: String) : ChatListUiState()
}

// ChatDetailViewModel.kt
class ChatDetailViewModel(
    private val roomId: ChatRoom.RoomId,
    private val sendMessageUseCase: SendMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val observeTypingUseCase: ObserveTypingUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()
    
    init {
        loadInitialData()
        observeMessages()
        observeTypingIndicators()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            val currentUser = getCurrentUserUseCase()
            _uiState.update { it.copy(currentUserId = currentUser?.id) }
        }
    }
    
    private fun observeMessages() {
        observeMessagesUseCase(roomId)
            .onEach { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
            .launchIn(viewModelScope)
    }
    
    private fun observeTypingIndicators() {
        observeTypingUseCase(roomId)
            .onEach { typingUsers ->
                _uiState.update { it.copy(typingUsers = typingUsers) }
            }
            .launchIn(viewModelScope)
    }
    
    fun updateMessageText(text: String) {
        _uiState.update { it.copy(messageText = text) }
    }
    
    fun sendMessage() {
        val text = _uiState.value.messageText.trim()
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(messageText = "", isSending = true) }
            
            sendMessageUseCase(
                SendMessageUseCase.SendMessageParams(
                    roomId = roomId,
                    senderId = _uiState.value.currentUserId ?: return@launch,
                    content = Message.MessageContent.Text(text)
                )
            ).fold(
                onSuccess = {
                    // Message sent successfully
                },
                onFailure = { error ->
                    // Show error
                    _uiState.update { 
                        it.copy(
                            messageText = text,
                            error = error.message
                        )
                    }
                }
            )
            
            _uiState.update { it.copy(isSending = false) }
        }
    }
    
    fun sendTypingIndicator() {
        // Implement typing indicator logic
    }
}

data class ChatDetailUiState(
    val roomName: String = "",
    val messages: List<Message> = emptyList(),
    val messageText: String = "",
    val currentUserId: User.UserId? = null,
    val typingUsers: List<User> = emptyList(),
    val isSending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
) {
    val canSendMessage: Boolean = !isSending && currentUserId != null
}
```

## Real-Time Features

### WebSocket Message Types

```kotlin
// WebSocketMessage.kt
@Serializable
sealed class WebSocketMessage {
    @Serializable
    @SerialName("connected")
    data class Connected(
        val userId: String,
        val timestamp: Instant
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("send_message")
    data class SendMessage(
        val roomId: String,
        val content: MessageContentDto,
        val replyToId: String? = null
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("message_received")
    data class MessageReceived(
        val message: MessageDto
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("typing_indicator")
    data class TypingIndicator(
        val roomId: String,
        val isTyping: Boolean
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("user_typing")
    data class UserTyping(
        val userId: String,
        val roomId: String,
        val isTyping: Boolean
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("presence_update")
    data class PresenceUpdate(
        val userId: String,
        val status: String,
        val lastSeen: Instant
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("mark_as_read")
    data class MarkAsRead(
        val messageIds: List<String>
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("message_status_update")
    data class MessageStatusUpdate(
        val messageId: String,
        val status: String,
        val userId: String
    ) : WebSocketMessage()
}
```

### Real-Time Message Sync

```kotlin
// MessageSyncService.kt
class MessageSyncService(
    private val apiClient: ChatApiClient,
    private val messageRepository: MessageRepository,
    private val connectivityObserver: ConnectivityObserver
) {
    private val pendingMessages = mutableListOf<Message>()
    private val syncJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + syncJob)
    
    init {
        observeConnectivity()
    }
    
    private fun observeConnectivity() {
        connectivityObserver.observe()
            .onEach { isConnected ->
                if (isConnected) {
                    syncPendingMessages()
                }
            }
            .launchIn(scope)
    }
    
    private suspend fun syncPendingMessages() {
        pendingMessages.toList().forEach { message ->
            try {
                messageRepository.sendMessage(message)
                pendingMessages.remove(message)
            } catch (e: Exception) {
                // Keep in pending queue
            }
        }
    }
    
    fun queueMessage(message: Message) {
        pendingMessages.add(message)
        
        scope.launch {
            if (connectivityObserver.isConnected()) {
                syncPendingMessages()
            }
        }
    }
}
```

## Authentication & Security

### JWT Authentication

```kotlin
// AuthService.kt
class AuthService(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager
) {
    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return safeApiCall {
            val response = httpClient.post("$BASE_URL/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }.body<AuthResponse>()
            
            tokenManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken
            )
            
            response
        }
    }
    
    suspend fun refreshToken(): Result<AuthResponse> {
        return safeApiCall {
            val refreshToken = tokenManager.getRefreshToken()
                ?: throw IllegalStateException("No refresh token")
            
            val response = httpClient.post("$BASE_URL/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }.body<AuthResponse>()
            
            tokenManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken
            )
            
            response
        }
    }
    
    suspend fun logout() {
        tokenManager.clearTokens()
        // Clear local data
    }
}

// TokenManager.kt
class TokenManager(
    private val secureStorage: SecureStorage
) {
    private var accessTokenCache: String? = null
    private var refreshTokenCache: String? = null
    
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        accessTokenCache = accessToken
        refreshTokenCache = refreshToken
        
        secureStorage.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStorage.putString(KEY_REFRESH_TOKEN, refreshToken)
    }
    
    suspend fun getAccessToken(): String? {
        return accessTokenCache ?: secureStorage.getString(KEY_ACCESS_TOKEN)
            ?.also { accessTokenCache = it }
    }
    
    suspend fun getRefreshToken(): String? {
        return refreshTokenCache ?: secureStorage.getString(KEY_REFRESH_TOKEN)
            ?.also { refreshTokenCache = it }
    }
    
    suspend fun clearTokens() {
        accessTokenCache = null
        refreshTokenCache = null
        
        secureStorage.remove(KEY_ACCESS_TOKEN)
        secureStorage.remove(KEY_REFRESH_TOKEN)
    }
    
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}

// AuthInterceptor.kt
class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val authService: AuthService
) {
    suspend fun intercept(request: HttpRequestBuilder): HttpRequestBuilder {
        val token = tokenManager.getAccessToken()
        
        if (token != null) {
            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }
        
        return request
    }
    
    suspend fun handleUnauthorized() {
        authService.refreshToken().fold(
            onSuccess = {
                // Token refreshed successfully
            },
            onFailure = {
                // Redirect to login
                authService.logout()
            }
        )
    }
}
```

## Media Handling

### Image Upload and Compression

```kotlin
// MediaUploadService.kt
class MediaUploadService(
    private val httpClient: HttpClient,
    private val imageCompressor: ImageCompressor
) {
    suspend fun uploadImage(
        imageBytes: ByteArray,
        roomId: ChatRoom.RoomId
    ): Result<MediaUploadResponse> {
        return runCatching {
            // Compress image
            val compressedImage = imageCompressor.compress(
                imageBytes = imageBytes,
                maxWidth = 1920,
                maxHeight = 1920,
                quality = 85
            )
            
            // Generate thumbnail
            val thumbnail = imageCompressor.compress(
                imageBytes = imageBytes,
                maxWidth = 200,
                maxHeight = 200,
                quality = 70
            )
            
            // Upload
            httpClient.post("$BASE_URL/media/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", compressedImage, Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                            })
                            append("thumbnail", thumbnail, Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                            })
                            append("roomId", roomId.value)
                        }
                    )
                )
            }.body()
        }
    }
}

// Platform-specific image compression
expect class ImageCompressor {
    suspend fun compress(
        imageBytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int
    ): ByteArray
}

// Android implementation
actual class ImageCompressor {
    actual suspend fun compress(
        imageBytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int
    ): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        val scaledBitmap = if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth: Int
            val newHeight: Int
            
            if (aspectRatio > 1) {
                newWidth = maxWidth
                newHeight = (maxWidth / aspectRatio).toInt()
            } else {
                newHeight = maxHeight
                newWidth = (maxHeight * aspectRatio).toInt()
            }
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        outputStream.toByteArray()
    }
}
```

## Testing Strategy

### Unit Tests

```kotlin
// SendMessageUseCaseTest.kt
class SendMessageUseCaseTest : StringSpec({
    lateinit var messageRepository: MessageRepository
    lateinit var roomRepository: ChatRoomRepository
    lateinit var useCase: SendMessageUseCase
    
    beforeTest {
        messageRepository = mockk()
        roomRepository = mockk()
        useCase = SendMessageUseCase(messageRepository, roomRepository)
    }
    
    "should send message successfully when user is participant" {
        // Given
        val roomId = ChatRoom.RoomId("room-1")
        val userId = User.UserId("user-1")
        val content = Message.MessageContent.Text("Hello")
        
        val room = ChatRoom(
            id = roomId,
            name = "Test Room",
            type = ChatRoom.RoomType.GROUP,
            participants = listOf(userId),
            lastMessage = null,
            unreadCount = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        coEvery { roomRepository.getRoom(roomId) } returns room
        coEvery { messageRepository.sendMessage(any()) } answers {
            firstArg<Message>()
        }
        
        // When
        val result = useCase(
            SendMessageUseCase.SendMessageParams(
                roomId = roomId,
                senderId = userId,
                content = content
            )
        )
        
        // Then
        result.shouldBeSuccess()
        coVerify { messageRepository.sendMessage(any()) }
    }
    
    "should fail when user is not participant" {
        // Given
        val roomId = ChatRoom.RoomId("room-1")
        val userId = User.UserId("user-1")
        val content = Message.MessageContent.Text("Hello")
        
        val room = ChatRoom(
            id = roomId,
            name = "Test Room",
            type = ChatRoom.RoomType.GROUP,
            participants = listOf(User.UserId("other-user")),
            lastMessage = null,
            unreadCount = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        coEvery { roomRepository.getRoom(roomId) } returns room
        
        // When
        val result = useCase(
            SendMessageUseCase.SendMessageParams(
                roomId = roomId,
                senderId = userId,
                content = content
            )
        )
        
        // Then
        result.shouldBeFailure()
        coVerify(exactly = 0) { messageRepository.sendMessage(any()) }
    }
})

// Integration Tests
class ChatApiIntegrationTest : StringSpec({
    lateinit var client: HttpClient
    lateinit var server: MockWebServer
    lateinit var apiClient: ChatApiClient
    
    beforeSpec {
        server = MockWebServer()
        server.start()
        
        client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }
        
        apiClient = ChatApiClient(
            httpClient = client,
            websocketClient = client,
            tokenManager = mockk()
        )
    }
    
    afterSpec {
        server.shutdown()
    }
    
    "should fetch rooms successfully" {
        // Given
        val rooms = listOf(
            ChatRoomDto(
                id = "room-1",
                name = "General",
                type = "GROUP"
            )
        )
        
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Json.encodeToString(rooms))
                .addHeader("Content-Type", "application/json")
        )
        
        // When
        val result = apiClient.getRooms()
        
        // Then
        result.shouldBeSuccess()
        result.getOrNull()?.size shouldBe 1
        result.getOrNull()?.first()?.name shouldBe "General"
    }
})
```

### UI Tests

```kotlin
// ChatDetailScreenTest.kt
class ChatDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testSendMessage() {
        // Given
        val viewModel = mockk<ChatDetailViewModel>(relaxed = true)
        val uiState = MutableStateFlow(
            ChatDetailUiState(
                roomName = "Test Room",
                messages = emptyList(),
                messageText = "",
                currentUserId = User.UserId("user-1")
            )
        )
        
        every { viewModel.uiState } returns uiState
        
        composeTestRule.setContent {
            ChatDetailScreen(
                roomId = ChatRoom.RoomId("room-1"),
                onBackClick = {},
                viewModel = viewModel
            )
        }
        
        // When
        composeTestRule
            .onNodeWithText("Type a message...")
            .performTextInput("Hello World")
        
        composeTestRule
            .onNodeWithContentDescription("Send")
            .performClick()
        
        // Then
        verify { viewModel.updateMessageText("Hello World") }
        verify { viewModel.sendMessage() }
    }
}
```

## Performance Optimization

### Message Pagination

```kotlin
// PaginatedMessageRepository.kt
class PaginatedMessageRepository(
    private val apiClient: ChatApiClient,
    private val messageDao: MessageDao
) : MessageRepository {
    
    private val pageSize = 30
    private val messagePagingConfig = PagingConfig(
        pageSize = pageSize,
        enablePlaceholders = false,
        prefetchDistance = pageSize / 2
    )
    
    fun getMessagesPaged(roomId: ChatRoom.RoomId): Flow<PagingData<Message>> {
        return Pager(
            config = messagePagingConfig,
            remoteMediator = MessageRemoteMediator(roomId, apiClient, messageDao),
            pagingSourceFactory = { messageDao.getMessagesPagingSource(roomId.value) }
        ).flow.map { pagingData ->
            pagingData.map { it.toEntity() }
        }
    }
}

// MessageRemoteMediator.kt
class MessageRemoteMediator(
    private val roomId: ChatRoom.RoomId,
    private val apiClient: ChatApiClient,
    private val messageDao: MessageDao
) : RemoteMediator<Int, LocalMessage>() {
    
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, LocalMessage>
    ): MediatorResult {
        return try {
            val loadKey = when (loadType) {
                LoadType.REFRESH -> null
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)
                    lastItem.timestamp
                }
            }
            
            val messages = apiClient.getMessages(
                roomId = roomId.value,
                before = loadKey?.let { Instant.fromEpochMilliseconds(it) },
                limit = state.config.pageSize
            ).getOrThrow()
            
            messageDao.insertMessages(messages.map { it.toEntity().toLocal() })
            
            MediatorResult.Success(
                endOfPaginationReached = messages.size < state.config.pageSize
            )
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
```

### Image Caching

```kotlin
// ImageLoader configuration
fun createImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(100 * 1024 * 1024) // 100MB
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .componentRegistry {
            add(ChatImageFetcher.Factory())
        }
        .build()
}

// Custom image fetcher with auth
class ChatImageFetcher(
    private val url: String,
    private val tokenManager: TokenManager,
    private val httpClient: HttpClient
) : Fetcher {
    
    override suspend fun fetch(): FetchResult {
        val token = tokenManager.getAccessToken()
            ?: throw IllegalStateException("No auth token")
        
        val response = httpClient.get(url) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        
        return SourceResult(
            source = response.bodyAsChannel().toImageSource(),
            mimeType = response.contentType()?.toString(),
            dataSource = DataSource.NETWORK
        )
    }
    
    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.startsWith("https://api.chatapp.com/media/")) {
                ChatImageFetcher(data, tokenManager, httpClient)
            } else null
        }
    }
}
```

## Deployment & DevOps

### Server Deployment

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: chatapp
      POSTGRES_USER: chatuser
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
  
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"
  
  chat-server:
    build:
      context: ./server
      dockerfile: Dockerfile
    environment:
      DATABASE_URL: postgresql://chatuser:${DB_PASSWORD}@postgres:5432/chatapp
      REDIS_URL: redis://redis:6379
      JWT_SECRET: ${JWT_SECRET}
      PORT: 8080
    depends_on:
      - postgres
      - redis
    ports:
      - "8080:8080"
  
  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - chat-server

volumes:
  postgres_data:
  redis_data:

# Dockerfile
FROM gradle:8-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

### CI/CD Pipeline

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run tests
        run: ./gradlew test
      
      - name: Upload test results
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: '**/build/test-results/**/*.xml'
  
  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build server Docker image
        run: |
          docker build -t chatapp-server:${{ github.sha }} ./server
          docker tag chatapp-server:${{ github.sha }} chatapp-server:latest
      
      - name: Push to registry
        run: |
          echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
          docker push chatapp-server:${{ github.sha }}
          docker push chatapp-server:latest
  
  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to production
        uses: appleboy/ssh-action@v0.1.5
        with:
          host: ${{ secrets.PRODUCTION_HOST }}
          username: ${{ secrets.PRODUCTION_USER }}
          key: ${{ secrets.PRODUCTION_SSH_KEY }}
          script: |
            cd /opt/chatapp
            docker-compose pull
            docker-compose up -d --remove-orphans
            docker image prune -f
```

## Conclusion

This comprehensive guide covers all aspects of building a production-ready real-time chat application using Ktor and Kotlin Compose Multiplatform with clean architecture. The implementation includes:

- Clean architecture with clear separation of concerns
- Real-time WebSocket communication
- Offline support and message synchronization
- Cross-platform UI with Compose Multiplatform
- Comprehensive testing strategy
- Performance optimizations
- Production deployment setup

Remember to adapt these patterns and implementations to your specific requirements and scale accordingly.