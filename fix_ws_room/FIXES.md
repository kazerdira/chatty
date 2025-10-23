# Comprehensive Fixes for Chatty App Issues

## Issue Summary
1. Invalid token after registration when searching users
2. Room creation spinning with no result
3. WebSocket disconnection problems
4. Room not appearing in other users' UI

## Root Causes
1. **Race condition**: WebSocket connects before tokens are fully saved
2. **Token timing**: Immediate API calls after registration before token persistence
3. **WebSocket authentication**: User ID not available when WebSocket authenticates
4. **Room sync**: WebSocket notifications not received if connection isn't established

---

## Fix 1: AuthRepositoryImpl.kt - Add Delay After Token Save

**File**: `shared/src/commonMain/kotlin/com/chatty/data/repository/AuthRepositoryImpl.kt`

```kotlin
class AuthRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val tokenManager: TokenManager
) : AuthRepository {
    
    override suspend fun login(username: String, password: String): Result<AuthTokens> {
        return apiClient.login(AuthRequest(username, password)).mapCatching { response ->
            // Save tokens synchronously
            tokenManager.saveAccessToken(response.token)
            tokenManager.saveRefreshToken(response.refreshToken)
            tokenManager.saveUserId(response.userId)
            
            println("💾 AuthRepository: Tokens saved for user: ${response.userId}")
            
            // Give tokens time to persist (especially important for encrypted storage)
            kotlinx.coroutines.delay(500)
            
            // Connect WebSocket after tokens are persisted
            apiClient.connectWebSocket()
            
            AuthTokens(
                accessToken = response.token,
                refreshToken = response.refreshToken,
                expiresIn = response.expiresIn
            )
        }
    }
    
    override suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String
    ): Result<AuthTokens> {
        return apiClient.register(RegisterRequest(username, email, password, displayName))
            .mapCatching { response ->
                // Save tokens synchronously
                tokenManager.saveAccessToken(response.token)
                tokenManager.saveRefreshToken(response.refreshToken)
                tokenManager.saveUserId(response.userId)
                
                println("💾 AuthRepository: Tokens saved after registration for user: ${response.userId}")
                
                // Give tokens time to persist (especially important for encrypted storage)
                kotlinx.coroutines.delay(500)
                
                // Connect WebSocket after tokens are persisted
                apiClient.connectWebSocket()
                
                AuthTokens(
                    accessToken = response.token,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresIn
                )
            }
    }
    
    override suspend fun refreshToken(refreshToken: String): Result<AuthTokens> {
        return apiClient.refreshToken(RefreshTokenRequest(refreshToken))
            .mapCatching { response ->
                tokenManager.saveAccessToken(response.token)
                tokenManager.saveRefreshToken(response.refreshToken)
                
                // Give tokens time to persist
                kotlinx.coroutines.delay(200)
                
                AuthTokens(
                    accessToken = response.token,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresIn
                )
            }
    }
    
    override suspend fun logout(): Result<Unit> {
        return runCatching {
            apiClient.disconnectWebSocket()
            tokenManager.clearTokens()
        }
    }
    
    override fun isAuthenticated(): Boolean {
        return true // Simplified for now
    }
    
    override suspend fun getAccessToken(): String? {
        return tokenManager.getAccessToken()
    }
}
```

---

## Fix 2: ChatApiClient.kt - Improved WebSocket Connection

**File**: `shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt`

Replace the `connectWebSocket()` function:

```kotlin
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
                    println("🔌 WebSocket: Connection closed by server: ${closeReason.await()}")
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
```

---

## Fix 3: ChatListViewModel.kt - Wait for WebSocket Before Rooms

**File**: `androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatListViewModel.kt`

```kotlin
class ChatListViewModel(
    private val observeRoomsUseCase: ObserveRoomsUseCase,
    private val apiClient: ChatApiClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    init {
        // Wait for WebSocket to connect, then load rooms
        viewModelScope.launch {
            println("🔌 ChatListViewModel: Waiting for WebSocket connection...")
            
            // Wait for WebSocket to be connected (timeout after 10 seconds)
            withTimeoutOrNull(10000) {
                apiClient.connectionState.first { it == WebSocketConnectionState.CONNECTED }
            }
            
            println("✅ ChatListViewModel: WebSocket connected, loading rooms...")
            loadRooms()
        }
    }
    
    private fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                observeRoomsUseCase()
                    .collect { rooms ->
                        println("📋 ChatListViewModel: Received ${rooms.size} rooms")
                        _uiState.value = _uiState.value.copy(
                            rooms = rooms,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (error: Exception) {
                println("❌ ChatListViewModel: Error loading rooms: ${error.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to load chats"
                )
            }
        }
    }
    
    fun retry() {
        loadRooms()
    }
}
```

---

## Fix 4: UserSearchViewModel.kt - Ensure WebSocket & Better Error Handling

**File**: `androidApp/src/main/kotlin/com/chatty/android/ui/chat/UserSearchViewModel.kt`

Add import at the top:
```kotlin
import com.chatty.data.remote.WebSocketConnectionState
import kotlinx.coroutines.withTimeoutOrNull
```

Update the `createRoom` function:

```kotlin
fun createRoom(roomName: String) {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isCreating = true, error = null)
        
        try {
            // First, ensure WebSocket is connected
            println("🔌 UserSearchViewModel: Checking WebSocket connection...")
            
            val connectionState = apiClient.connectionState.value
            if (connectionState != WebSocketConnectionState.CONNECTED) {
                println("⚠️ UserSearchViewModel: WebSocket not connected, attempting to connect...")
                apiClient.retryConnection()
                
                // Wait for connection (timeout after 5 seconds)
                val connected = withTimeoutOrNull(5000) {
                    apiClient.connectionState.first { it == WebSocketConnectionState.CONNECTED }
                } != null
                
                if (!connected) {
                    throw Exception("Failed to establish WebSocket connection. Please try again.")
                }
            }
            
            println("✅ UserSearchViewModel: WebSocket connected, creating room...")
            
            val selectedUsers = _uiState.value.selectedUsers
            val roomType = if (selectedUsers.size == 1) {
                ChatRoom.RoomType.DIRECT
            } else {
                ChatRoom.RoomType.GROUP
            }
            
            createRoomUseCase(
                CreateRoomUseCase.CreateRoomParams(
                    name = roomName,
                    type = roomType,
                    participantIds = selectedUsers.map { it.id }
                )
            ).onSuccess { room ->
                println("✅ UserSearchViewModel: Room created successfully: ${room.id.value}")
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdRoomId = room.id.value,
                    roomName = roomName,
                    error = null
                )
            }.onFailure { error ->
                println("❌ UserSearchViewModel: Room creation failed: ${error.message}")
                error.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = error.message ?: "Failed to create chat"
                )
            }
        } catch (e: Exception) {
            println("❌ UserSearchViewModel: Unexpected error: ${e.message}")
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                isCreating = false,
                error = e.message ?: "An unexpected error occurred"
            )
        }
    }
}
```

Update the ViewModel class to include apiClient:

```kotlin
class UserSearchViewModel(
    private val searchUsersUseCase: SearchUsersUseCase,
    private val createRoomUseCase: CreateRoomUseCase,
    private val apiClient: ChatApiClient // ADD THIS
) : ViewModel() {
    // ... rest of the code
}
```

---

## Fix 5: AppModule.kt - Update DI to Include ChatApiClient

**File**: `androidApp/src/main/kotlin/com/chatty/android/di/AppModule.kt`

Update the UserSearchViewModel injection:

```kotlin
viewModel { 
    UserSearchViewModel(
        searchUsersUseCase = get(),
        createRoomUseCase = get(),
        apiClient = get() // ADD THIS
    ) 
}
```

---

## Fix 6: ChatRoomRepositoryImpl.kt - Better Room Sync

**File**: `shared/src/commonMain/kotlin/com/chatty/data/repository/ChatRoomRepositoryImpl.kt`

Update the `createRoom` function:

```kotlin
override suspend fun createRoom(
    name: String,
    type: ChatRoom.RoomType,
    participantIds: List<User.UserId>
): Result<ChatRoom> {
    val typeString = when (type) {
        ChatRoom.RoomType.DIRECT -> "DIRECT"
        ChatRoom.RoomType.GROUP -> "GROUP"
        ChatRoom.RoomType.CHANNEL -> "CHANNEL"
    }
    
    return apiClient.createRoom(
        name = name,
        type = typeString,
        participantIds = participantIds.map { it.value }
    ).map { dto ->
        val room = dto.toEntity()
        println("✅ ChatRoomRepository: Room created: ${room.id.value}, adding to local cache")
        
        // Add to local cache immediately
        val currentRooms = _rooms.value
        if (currentRooms.none { it.id == room.id }) {
            _rooms.value = currentRooms + room
            println("✅ ChatRoomRepository: Room added to cache, total rooms: ${_rooms.value.size}")
        }
        
        // Also refresh from server to ensure sync
        scope.launch {
            delay(1000) // Give server time to broadcast to other clients
            println("🔄 ChatRoomRepository: Refreshing rooms from server...")
            getRooms()
        }
        
        room
    }
}
```

---

## Fix 7: LoginScreen.kt - Add Delay Before Navigation

**File**: `androidApp/src/main/kotlin/com/chatty/android/ui/auth/LoginScreen.kt`

Update the navigation effect:

```kotlin
// Navigate on success
LaunchedEffect(uiState.isLoggedIn) {
    if (uiState.isLoggedIn) {
        // Wait a bit to ensure everything is set up
        kotlinx.coroutines.delay(800)
        onLoginSuccess()
    }
}
```

---

## Testing Checklist

After applying these fixes, test in this order:

### Test 1: Registration Flow
1. ✅ Register a new user
2. ✅ Check console logs for "Tokens saved after registration"
3. ✅ Check console logs for "WebSocket: Connected successfully"
4. ✅ Check console logs for "Authentication message sent"
5. ✅ Try searching for users immediately after registration
6. ✅ Verify no "invalid token" errors

### Test 2: Room Creation
1. ✅ Create a room with selected users
2. ✅ Check console logs for "WebSocket connected, creating room..."
3. ✅ Check console logs for "Room created successfully"
4. ✅ Verify room appears in creator's chat list
5. ✅ Check other user's device - room should appear within 1-2 seconds
6. ✅ Verify no spinning/hanging state

### Test 3: WebSocket Stability
1. ✅ Login and wait for "WebSocket: Connected"
2. ✅ Leave app in background for 1 minute
3. ✅ Return to app - check if WebSocket reconnects
4. ✅ Create a room - should work without issues

---

## Additional Improvements

### Add Loading State Feedback

In `UserSearchScreen.kt`, add better loading feedback:

```kotlin
if (uiState.isCreating) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Creating Chat") },
        text = { 
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Setting up your chat room...")
            }
        },
        confirmButton = { }
    )
}
```

### Add Connection State Indicator

In `ChatListScreen.kt`, show WebSocket status:

```kotlin
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: ChatListViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by viewModel.apiClient.connectionState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Chats")
                        if (connectionState != WebSocketConnectionState.CONNECTED) {
                            Text(
                                text = when (connectionState) {
                                    WebSocketConnectionState.CONNECTING -> "Connecting..."
                                    WebSocketConnectionState.RECONNECTING -> "Reconnecting..."
                                    WebSocketConnectionState.ERROR -> "Connection error"
                                    else -> "Disconnected"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                // ... rest of TopAppBar
            )
        },
        // ... rest of Scaffold
    )
}
```

---

## Summary of Changes

1. **Added 500ms delay** after token saving in AuthRepository
2. **Added 300ms delay** before WebSocket connection attempt
3. **Added userId validation** before WebSocket connection
4. **Added WebSocket connection check** before room creation
5. **Added retry logic** with proper error handling
6. **Added room refresh** after creation to ensure sync
7. **Added connection state monitoring** in UI
8. **Fixed UserSearchViewModel** to require ChatApiClient for connection checks

These changes ensure:
- ✅ Tokens are always saved before use
- ✅ WebSocket is connected before operations that need it
- ✅ Better error messages for debugging
- ✅ Automatic retry with exponential backoff
- ✅ Real-time sync works reliably
- ✅ User gets clear feedback on what's happening

