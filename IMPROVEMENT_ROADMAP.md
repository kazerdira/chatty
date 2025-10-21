# 🚀 Chatty App - Improvement Roadmap

**Current Status**: ✅ MVP Complete (Android + Backend APIs working)  
**Next Phase**: Production-Ready Features

---

## ✅ What's Already Done Well

### Architecture & Foundation
- ✅ **Clean Architecture Structure** - Excellent separation of concerns (domain, data, presentation)
- ✅ **Domain Models** - Good use of value classes and sealed classes
- ✅ **Repository Pattern** - Proper abstraction between data sources
- ✅ **Platform-Specific Implementations** - TokenManager for Android/Desktop
- ✅ **Basic WebSocket Client** - Foundation for real-time communication
- ✅ **DTOs with Mappers** - Clean separation between network and domain models
- ✅ **REST API Complete** - All CRUD operations working
- ✅ **JWT Authentication** - Secure token-based auth
- ✅ **Android App UI** - Complete with Jetpack Compose + Material 3
- ✅ **Koin DI** - Dependency injection properly configured

---

## 🔧 Key Improvements Needed

### Priority 1: Critical Features (1-2 weeks)

#### 1. Server-Side WebSocket Implementation ⚠️ High Priority

**Current State**: Echo server only  
**Needed**: Full message broadcasting with room support

```kotlin
// server/src/main/kotlin/com/chatty/server/websocket/ConnectionManager.kt
class ConnectionManager {
    private val connections = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val roomConnections = ConcurrentHashMap<String, MutableSet<String>>()
    
    fun addUserConnection(userId: String, session: WebSocketSession) {
        connections.getOrPut(userId) { ConcurrentHashMap.newKeySet() }.add(session)
        updateUserStatus(userId, UserStatus.ONLINE)
    }
    
    fun removeUserConnection(userId: String, session: WebSocketSession) {
        connections[userId]?.remove(session)
        if (connections[userId]?.isEmpty() == true) {
            connections.remove(userId)
            updateUserStatus(userId, UserStatus.OFFLINE)
        }
    }
    
    fun joinRoom(userId: String, roomId: String) {
        roomConnections.getOrPut(roomId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }
    
    suspend fun broadcastToRoom(roomId: String, message: WebSocketMessage, excludeUser: String? = null) {
        roomConnections[roomId]?.forEach { userId ->
            if (userId != excludeUser) {
                connections[userId]?.forEach { session ->
                    try {
                        session.send(Frame.Text(Json.encodeToString(message)))
                    } catch (e: Exception) {
                        removeUserConnection(userId, session)
                    }
                }
            }
        }
    }
}

// In Application.kt - Update WebSocket route
webSocket("/ws") {
    val principal = call.principal<JWTPrincipal>()
    val userId = principal?.payload?.getClaim("userId")?.asString() 
        ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No userId"))
    
    connectionManager.addUserConnection(userId, this)
    
    try {
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    val message = Json.decodeFromString<WebSocketMessage>(String(frame.data))
                    handleWebSocketMessage(userId, message, connectionManager)
                }
                else -> {}
            }
        }
    } finally {
        connectionManager.removeUserConnection(userId, this)
    }
}
```

**Tasks**:
- [ ] Implement ConnectionManager class
- [ ] Add room join/leave logic
- [ ] Implement message broadcasting
- [ ] Add user status tracking (online/offline)
- [ ] Handle WebSocket disconnections gracefully
- [ ] Add reconnection logic on client side

---

#### 2. Replace Mock Database with PostgreSQL ⚠️ High Priority

**Current State**: In-memory ConcurrentHashMap  
**Needed**: Persistent PostgreSQL with Exposed ORM

```kotlin
// server/src/main/kotlin/com/chatty/server/data/DatabaseTables.kt
object Users : Table() {
    val id = varchar("id", 36).primaryKey()
    val username = varchar("username", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val passwordHash = varchar("password_hash", 100)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val status = enumeration("status", UserStatus::class).default(UserStatus.OFFLINE)
    val lastSeen = timestamp("last_seen")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}

object ChatRooms : Table() {
    val id = varchar("id", 36).primaryKey()
    val name = varchar("name", 100)
    val type = enumeration("type", RoomType::class)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

object RoomParticipants : Table() {
    val roomId = reference("room_id", ChatRooms.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp())
    val role = enumeration("role", ParticipantRole::class).default(ParticipantRole.MEMBER)
    
    override val primaryKey = PrimaryKey(roomId, userId)
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
    val status = enumeration("status", MessageStatusEnum::class)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
    
    override val primaryKey = PrimaryKey(messageId, userId)
}

// DatabaseFactory.kt
object DatabaseFactory {
    fun init(config: DatabaseConfig) {
        Database.connect(
            url = config.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = config.user,
            password = config.password
        )
        
        transaction {
            SchemaUtils.create(
                Users,
                ChatRooms,
                RoomParticipants,
                Messages,
                MessageStatus
            )
        }
    }
}
```

**Tasks**:
- [ ] Add PostgreSQL dependency to server/build.gradle.kts
- [ ] Create database schema with Exposed
- [ ] Implement repository implementations using Exposed
- [ ] Add database migrations
- [ ] Update docker-compose.yml with PostgreSQL service
- [ ] Add connection pooling (HikariCP)

---

#### 3. Complete Client-Side Message Status Flow

**Current State**: Messages sent but no status tracking  
**Needed**: Full SENT → DELIVERED → READ flow

```kotlin
// In MessageRepositoryImpl.kt
override suspend fun sendMessage(message: Message): Result<Message> = withContext(dispatcher) {
    // 1. Store locally with SENDING status
    database.chatDatabaseQueries.insertMessage(
        id = message.id.value,
        roomId = message.roomId.value,
        senderId = message.senderId.value,
        senderName = "Current User", // Get from UserRepository
        contentType = message.content::class.simpleName!!,
        contentData = Json.encodeToString(message.content),
        timestamp = message.timestamp.toEpochMilliseconds(),
        status = Message.MessageStatus.SENDING.name,
        editedAt = null,
        replyToId = message.replyTo?.value
    )
    
    // 2. Send via WebSocket
    apiClient.sendMessage(
        WebSocketMessage.SendMessage(
            roomId = message.roomId.value,
            content = message.content.toDto(),
            tempId = message.id.value
        )
    ).fold(
        onSuccess = { 
            // 3. Update to SENT
            database.chatDatabaseQueries.updateMessageStatus(
                status = Message.MessageStatus.SENT.name,
                id = message.id.value
            )
            Result.success(message.copy(status = Message.MessageStatus.SENT))
        },
        onFailure = { error ->
            // 4. Update to FAILED
            database.chatDatabaseQueries.updateMessageStatus(
                status = Message.MessageStatus.FAILED.name,
                id = message.id.value
            )
            // 5. Queue for retry
            offlineQueue.enqueue(message)
            Result.failure(error)
        }
    )
}

// Listen to WebSocket status updates
init {
    apiClient.incomingMessages
        .filter { it is WebSocketMessage.MessageStatusUpdate }
        .collect { wsMessage ->
            val update = wsMessage as WebSocketMessage.MessageStatusUpdate
            database.chatDatabaseQueries.updateMessageStatus(
                status = update.status.name,
                id = update.messageId
            )
        }
}
```

**Tasks**:
- [ ] Implement local message storage with status
- [ ] Add WebSocket status update messages
- [ ] Handle DELIVERED status (server → client)
- [ ] Handle READ status (when user opens chat)
- [ ] Add retry logic for failed messages
- [ ] Show status icons in UI (✓, ✓✓, ✓✓ blue)

---

### Priority 2: User Experience (2-3 weeks)

#### 4. Add SQLDelight Client Database

**File**: `shared/src/commonMain/sqldelight/com/chatty/database/ChatDatabase.sq`

```sql
-- Messages table
CREATE TABLE LocalMessage (
    id TEXT PRIMARY KEY NOT NULL,
    roomId TEXT NOT NULL,
    senderId TEXT NOT NULL,
    senderName TEXT NOT NULL,
    senderAvatar TEXT,
    contentType TEXT NOT NULL,
    contentData TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    status TEXT NOT NULL,
    editedAt INTEGER,
    replyToId TEXT,
    FOREIGN KEY (roomId) REFERENCES LocalChatRoom(id) ON DELETE CASCADE
);

-- Chat rooms table
CREATE TABLE LocalChatRoom (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    avatarUrl TEXT,
    lastMessageId TEXT,
    unreadCount INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);

-- Room participants
CREATE TABLE LocalRoomParticipant (
    roomId TEXT NOT NULL,
    userId TEXT NOT NULL,
    userName TEXT NOT NULL,
    userAvatar TEXT,
    role TEXT NOT NULL DEFAULT 'MEMBER',
    joinedAt INTEGER NOT NULL,
    PRIMARY KEY (roomId, userId),
    FOREIGN KEY (roomId) REFERENCES LocalChatRoom(id) ON DELETE CASCADE
);

-- Pending messages (offline queue)
CREATE TABLE PendingMessage (
    id TEXT PRIMARY KEY NOT NULL,
    roomId TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    retryCount INTEGER NOT NULL DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_messages_room_time ON LocalMessage(roomId, timestamp DESC);
CREATE INDEX idx_messages_status ON LocalMessage(status) WHERE status = 'SENDING' OR status = 'FAILED';
CREATE INDEX idx_pending_timestamp ON PendingMessage(timestamp);

-- Queries
selectMessagesByRoom:
SELECT * FROM LocalMessage 
WHERE roomId = ? 
ORDER BY timestamp DESC 
LIMIT ?;

selectMessagesBeforeTimestamp:
SELECT * FROM LocalMessage 
WHERE roomId = ? AND timestamp < ?
ORDER BY timestamp DESC 
LIMIT ?;

insertMessage:
INSERT OR REPLACE INTO LocalMessage VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateMessageStatus:
UPDATE LocalMessage SET status = ? WHERE id = ?;

deleteMessage:
UPDATE LocalMessage SET contentData = '[Deleted]', editedAt = ? WHERE id = ?;

selectAllRooms:
SELECT * FROM LocalChatRoom ORDER BY updatedAt DESC;

selectRoomById:
SELECT * FROM LocalChatRoom WHERE id = ?;

insertRoom:
INSERT OR REPLACE INTO LocalChatRoom VALUES (?, ?, ?, ?, ?, ?, ?, ?);

updateRoomUnreadCount:
UPDATE LocalChatRoom SET unreadCount = ? WHERE id = ?;

resetUnreadCount:
UPDATE LocalChatRoom SET unreadCount = 0 WHERE id = ?;

selectPendingMessages:
SELECT * FROM PendingMessage ORDER BY timestamp;

insertPendingMessage:
INSERT INTO PendingMessage VALUES (?, ?, ?, ?, ?);

deletePendingMessage:
DELETE FROM PendingMessage WHERE id = ?;

incrementRetryCount:
UPDATE PendingMessage SET retryCount = retryCount + 1 WHERE id = ?;
```

**Tasks**:
- [ ] Create SQLDelight schema files
- [ ] Generate database code
- [ ] Update repository implementations to use local DB
- [ ] Add database migrations support
- [ ] Implement offline-first architecture

---

#### 5. Implement Offline Message Queue

```kotlin
// shared/src/commonMain/kotlin/com/chatty/data/offline/OfflineMessageQueue.kt
class OfflineMessageQueue(
    private val database: ChatDatabase,
    private val apiClient: ChatApiClient,
    private val scope: CoroutineScope
) {
    private val maxRetries = 3
    
    suspend fun enqueueMessage(message: Message) {
        database.pendingMessagesQueries.insertPendingMessage(
            id = message.id.value,
            roomId = message.roomId.value,
            content = Json.encodeToString(message.content),
            timestamp = message.timestamp.toEpochMilliseconds(),
            retryCount = 0
        )
    }
    
    fun startProcessing() {
        scope.launch {
            apiClient.connectionState
                .filter { it == ConnectionState.CONNECTED }
                .collect {
                    processPendingMessages()
                }
        }
    }
    
    private suspend fun processPendingMessages() {
        val pending = database.pendingMessagesQueries.selectPendingMessages().executeAsList()
        
        pending.forEach { pendingMessage ->
            if (pendingMessage.retryCount >= maxRetries) {
                // Mark as permanently failed
                database.chatDatabaseQueries.updateMessageStatus(
                    status = Message.MessageStatus.FAILED.name,
                    id = pendingMessage.id
                )
                database.pendingMessagesQueries.deletePendingMessage(pendingMessage.id)
                return@forEach
            }
            
            val message = pendingMessage.toMessage()
            apiClient.sendMessage(message).fold(
                onSuccess = {
                    // Successfully sent
                    database.chatDatabaseQueries.updateMessageStatus(
                        status = Message.MessageStatus.SENT.name,
                        id = pendingMessage.id
                    )
                    database.pendingMessagesQueries.deletePendingMessage(pendingMessage.id)
                },
                onFailure = {
                    // Increment retry count
                    database.pendingMessagesQueries.incrementRetryCount(pendingMessage.id)
                }
            )
            
            delay(500) // Avoid overwhelming server
        }
    }
}
```

**Tasks**:
- [ ] Implement OfflineMessageQueue
- [ ] Add automatic retry with exponential backoff
- [ ] Show pending message indicator in UI
- [ ] Handle network state changes
- [ ] Add manual retry button for failed messages

---

#### 6. Add Typing Indicators

```kotlin
// In ChatDetailViewModel.kt
private var typingJob: Job? = null
private val typingDebounceDelay = 3000L

fun onTextChanged(text: String) {
    _uiState.update { it.copy(messageText = text) }
    
    if (text.isNotBlank()) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            sendTypingIndicator(true)
            delay(typingDebounceDelay)
            sendTypingIndicator(false)
        }
    } else {
        sendTypingIndicator(false)
    }
}

private suspend fun sendTypingIndicator(isTyping: Boolean) {
    apiClient.sendMessage(
        WebSocketMessage.TypingIndicator(
            roomId = currentRoomId.value,
            userId = currentUserId,
            isTyping = isTyping
        )
    )
}

// Listen to typing indicators from others
init {
    apiClient.incomingMessages
        .filterIsInstance<WebSocketMessage.UserTyping>()
        .filter { it.roomId == currentRoomId.value }
        .collect { typing ->
            if (typing.isTyping) {
                _uiState.update { state ->
                    state.copy(
                        typingUsers = state.typingUsers + typing.userName
                    )
                }
                
                // Auto-remove after timeout
                delay(typingDebounceDelay)
                _uiState.update { state ->
                    state.copy(
                        typingUsers = state.typingUsers - typing.userName
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        typingUsers = state.typingUsers - typing.userName
                    )
                }
            }
        }
}
```

**Tasks**:
- [ ] Add typing indicator WebSocket messages
- [ ] Implement debounced typing detection
- [ ] Show "User is typing..." in chat UI
- [ ] Handle multiple users typing
- [ ] Add timeout for stale indicators

---

### Priority 3: Advanced Features (3-4 weeks)

#### 7. Media Upload Support

```kotlin
// Add to MessageContent
sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    
    data class Image(
        val url: String,
        val thumbnailUrl: String,
        val width: Int,
        val height: Int,
        val size: Long
    ) : MessageContent()
    
    data class Video(
        val url: String,
        val thumbnailUrl: String,
        val duration: kotlin.time.Duration,
        val size: Long
    ) : MessageContent()
    
    data class File(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val size: Long
    ) : MessageContent()
    
    data class Voice(
        val url: String,
        val duration: kotlin.time.Duration,
        val waveform: List<Float>
    ) : MessageContent()
}

// Upload API
suspend fun uploadMedia(file: ByteArray, mimeType: String): Result<String> {
    return safeApiCall {
        httpClient.post("$baseUrl/media/upload") {
            bearerAuth(tokenManager.getAccessToken())
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", file, Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                    })
                }
            ))
        }.body<MediaUploadResponse>().url
    }
}
```

**Tasks**:
- [ ] Add media upload endpoint to server
- [ ] Implement file storage (S3, MinIO, or local)
- [ ] Add image compression on client
- [ ] Generate thumbnails on server
- [ ] Support video trimming
- [ ] Add voice message recording
- [ ] Show upload progress
- [ ] Handle upload failures gracefully

---

#### 8. Message Reactions

```kotlin
data class MessageReaction(
    val messageId: Message.MessageId,
    val emoji: String,
    val userId: User.UserId,
    val timestamp: Instant
)

// Add to server
POST /messages/{messageId}/reactions
{
  "emoji": "👍"
}

DELETE /messages/{messageId}/reactions/{emoji}

// WebSocket message
@Serializable
data class ReactionAdded(
    val messageId: String,
    val emoji: String,
    val userId: String,
    val userName: String
) : WebSocketMessage()
```

**Tasks**:
- [ ] Add reaction database tables
- [ ] Implement reaction APIs
- [ ] Add reaction picker UI
- [ ] Show reaction counts on messages
- [ ] Broadcast reactions via WebSocket
- [ ] Add popular emoji suggestions

---

#### 9. Search & Filters

```kotlin
// Add search API
GET /messages/search?q=query&roomId=room-1&userId=user-2&before=timestamp&after=timestamp

// In ViewModel
fun searchMessages(query: String) {
    viewModelScope.launch {
        _searchState.value = SearchState.Loading
        
        messageRepository.searchMessages(
            query = query,
            roomId = currentRoomId,
            filters = SearchFilters(
                onlyMedia = filters.onlyMedia,
                onlyLinks = filters.onlyLinks,
                fromUser = filters.selectedUser
            )
        ).fold(
            onSuccess = { results ->
                _searchState.value = SearchState.Success(results)
            },
            onFailure = { error ->
                _searchState.value = SearchState.Error(error.message)
            }
        )
    }
}
```

**Tasks**:
- [ ] Add full-text search in database
- [ ] Implement message search API
- [ ] Add search UI with filters
- [ ] Support searching by user, date, media type
- [ ] Highlight search results
- [ ] Add "jump to message" feature

---

#### 10. Push Notifications

```kotlin
// Add Firebase Cloud Messaging
implementation("com.google.firebase:firebase-messaging:23.4.0")

class ChatFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val notification = remoteMessage.data.toNotification()
        
        NotificationManager.showNotification(
            title = notification.senderName,
            message = notification.messagePreview,
            roomId = notification.roomId,
            deepLink = "chatty://room/${notification.roomId}"
        )
    }
    
    override fun onNewToken(token: String) {
        // Send token to server
        apiClient.registerPushToken(token)
    }
}
```

**Tasks**:
- [ ] Set up Firebase project
- [ ] Add FCM to Android app
- [ ] Implement push notification service on server
- [ ] Store device tokens
- [ ] Send notifications for new messages
- [ ] Handle notification taps (deep linking)
- [ ] Add notification settings
- [ ] Support notification channels

---

### Priority 4: DevOps & Production (1 week)

#### 11. Docker Configuration

**File**: `docker-compose.yml`

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: chatty
      POSTGRES_USER: chatty
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./server/sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chatty"]
      interval: 10s
      timeout: 5s
      retries: 5
  
  redis:
    image: redis:7-alpine
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
  
  server:
    build:
      context: .
      dockerfile: server/Dockerfile
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/chatty
      DATABASE_USER: chatty
      DATABASE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      REDIS_URL: redis://redis:6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  postgres_data:
  redis_data:
```

**File**: `server/Dockerfile`

```dockerfile
FROM gradle:8.5-jdk19 AS build
WORKDIR /app
COPY . .
RUN gradle :server:shadowJar --no-daemon

FROM eclipse-temurin:19-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Tasks**:
- [ ] Create production Dockerfile
- [ ] Add docker-compose.yml
- [ ] Set up environment variables
- [ ] Add health check endpoints
- [ ] Configure reverse proxy (Nginx)
- [ ] Add SSL certificates
- [ ] Set up monitoring (Prometheus + Grafana)
- [ ] Add logging aggregation (ELK stack)

---

#### 12. CI/CD Pipeline

**File**: `.github/workflows/ci.yml`

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 19
        uses: actions/setup-java@v4
        with:
          java-version: '19'
          distribution: 'temurin'
      
      - name: Run tests
        run: ./gradlew test
      
      - name: Run integration tests
        run: ./gradlew integrationTest
      
      - name: Upload test results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-results
          path: '**/build/test-results/'
  
  build-android:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Build Android APK
        run: ./gradlew :androidApp:assembleRelease
      
      - name: Sign APK
        uses: r0adkll/sign-android-release@v1
        with:
          releaseDirectory: androidApp/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY }}
          alias: ${{ secrets.ALIAS }}
          keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
          keyPassword: ${{ secrets.KEY_PASSWORD }}
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-release
          path: androidApp/build/outputs/apk/release/*.apk
  
  build-server:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Build Docker image
        run: docker build -t chatty-server:${{ github.sha }} -f server/Dockerfile .
      
      - name: Push to registry
        run: |
          echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
          docker push chatty-server:${{ github.sha }}
  
  deploy:
    needs: [build-android, build-server]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Deploy to production
        run: |
          # Add deployment script here
          echo "Deploying to production..."
```

**Tasks**:
- [ ] Set up GitHub Actions
- [ ] Add automated testing
- [ ] Configure code coverage
- [ ] Add linting (ktlint, detekt)
- [ ] Set up deployment pipeline
- [ ] Add staging environment
- [ ] Configure secrets management

---

#### 13. Testing Strategy

```kotlin
// Unit Tests
class SendMessageUseCaseTest {
    @Test
    fun `should send message successfully`() = runTest {
        // Given
        val mockRepository = mockk<MessageRepository>()
        coEvery { mockRepository.sendMessage(any()) } returns Result.success(testMessage)
        
        val useCase = SendMessageUseCase(mockRepository)
        
        // When
        val result = useCase(SendMessageParams(...))
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { mockRepository.sendMessage(any()) }
    }
}

// Integration Tests
class ChatIntegrationTest {
    @Test
    fun `should handle full chat flow`() = runTest {
        // Given
        val testServer = embeddedServer(Netty, port = 0) {
            module()
        }.start()
        
        val client = createTestClient(testServer.port)
        
        // When
        client.login("testuser", "password")
        val rooms = client.getRooms()
        val messages = client.getMessages(rooms.first().id)
        client.sendMessage(rooms.first().id, "Hello")
        
        // Then
        assertTrue(messages.isNotEmpty())
        testServer.stop(0, 0)
    }
}

// UI Tests
@Test
fun testChatScreenShowsMessages() {
    composeTestRule.setContent {
        ChatScreen(roomId = "test-room-1")
    }
    
    composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
    composeTestRule.onNodeWithText("Send").performClick()
}
```

**Tasks**:
- [ ] Add unit tests (80%+ coverage)
- [ ] Add integration tests
- [ ] Add UI tests (Compose Testing)
- [ ] Add end-to-end tests
- [ ] Add performance tests
- [ ] Add load testing (Gatling, K6)
- [ ] Set up test coverage reporting

---

## 📊 Implementation Timeline

### Month 1: Core Improvements
- Week 1: WebSocket implementation + PostgreSQL setup
- Week 2: Message status tracking + Offline queue
- Week 3: SQLDelight integration + Typing indicators
- Week 4: Testing + Bug fixes

### Month 2: User Experience
- Week 1: Media upload + Reactions
- Week 2: Search & Filters
- Week 3: Push notifications
- Week 4: UI polish + Accessibility

### Month 3: Production Ready
- Week 1: Docker + CI/CD
- Week 2: Monitoring + Logging
- Week 3: Performance optimization
- Week 4: Documentation + Launch preparation

---

## 🎯 Success Metrics

### Technical Metrics
- [ ] 80%+ test coverage
- [ ] <200ms API response time (p95)
- [ ] 99.9% uptime
- [ ] <5% message failure rate
- [ ] <1s message delivery time

### User Metrics
- [ ] <3s app startup time
- [ ] <100ms UI response time
- [ ] Offline support for 7 days
- [ ] <50MB database size per 1000 messages
- [ ] Support 10,000+ concurrent users

---

## 📚 Additional Resources

### Documentation Needed
- [ ] API documentation (OpenAPI/Swagger)
- [ ] Architecture decision records (ADRs)
- [ ] Deployment guide
- [ ] User guide
- [ ] Contributing guidelines

### Third-Party Services
- [ ] Firebase (Push notifications)
- [ ] AWS S3 or MinIO (Media storage)
- [ ] Sentry (Error tracking)
- [ ] Mixpanel/Amplitude (Analytics)
- [ ] SendGrid (Email notifications)

---

## 🚀 Quick Wins (Can be done in 1-2 days)

1. ✅ Add loading states to all screens
2. ✅ Add pull-to-refresh
3. ✅ Add error retry buttons
4. ✅ Add empty states
5. ✅ Add skeleton loaders
6. ✅ Add haptic feedback
7. ✅ Add sound effects
8. ✅ Add dark mode support
9. ✅ Add app icon
10. ✅ Add splash screen

---

**Last Updated**: October 21, 2025  
**Status**: MVP Complete, Production Roadmap Defined  
**Next Milestone**: Priority 1 Features (2 weeks)
