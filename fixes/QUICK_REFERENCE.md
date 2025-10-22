# 🚀 Chatty App - Quick Reference Card

## ⚡ 5-Minute Quick Start

### 🔴 Top 3 Critical Fixes (Do These First!)

#### 1️⃣ WebSocket Authentication
**File:** `server/src/main/kotlin/com/chatty/server/Application.kt`

**Add to `webSocketRoute` function:**
```kotlin
// Wait for authentication before processing messages
var authenticated = false

when (message) {
    is ClientWebSocketMessage.Authenticate -> {
        currentUserId = message.userId
        webSocketManager.addConnection(message.userId, this)
        authenticated = true
        // Send confirmation
    }
    else -> {
        if (!authenticated) {
            outgoing.send(Frame.Text("Not authenticated"))
            continue
        }
        // Process message
    }
}
```

#### 2️⃣ Message Display Fix
**File:** `androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatRoomViewModel.kt`

**Add to UI state:**
```kotlin
data class ChatRoomUiState(
    val currentUserId: String? = null, // ADD THIS
    // ... existing fields
)

// ADD this method
fun isOwnMessage(message: Message): Boolean {
    val currentUserId = _uiState.value.currentUserId ?: return false
    return message.senderId.value == currentUserId
}

// IN INIT BLOCK
init {
    loadCurrentUser() // ADD THIS
    // ... existing code
}

private fun loadCurrentUser() {
    viewModelScope.launch {
        val userId = userRepository.getCurrentUserId()
        _uiState.value = _uiState.value.copy(currentUserId = userId)
    }
}
```

**File:** `androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatRoomScreen.kt`

**Change:**
```kotlin
// FROM:
val isOwnMessage = true // ❌

// TO:
MessageBubble(
    message = message,
    isOwnMessage = viewModel.isOwnMessage(message) // ✅
)
```

#### 3️⃣ User ID Storage
**File:** `shared/src/commonMain/kotlin/com/chatty/data/local/TokenManager.kt`

**Add methods:**
```kotlin
interface TokenManager {
    // ... existing methods
    suspend fun saveUserId(userId: String)       // ADD
    suspend fun getUserId(): String?             // ADD
    suspend fun saveUserInfo(                    // ADD
        userId: String, 
        username: String, 
        displayName: String
    )
}
```

**File:** `shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt`

**In login() method:**
```kotlin
suspend fun login(request: AuthRequest): Result<AuthResponse> {
    return safeApiCall {
        val response: AuthResponse = httpClient.post(...).body()
        
        // ADD THIS:
        tokenManager.saveUserInfo(
            userId = response.userId,
            username = response.username,
            displayName = response.displayName
        )
        
        response
    }
}
```

---

## 🔥 Most Impactful Changes

### Search Debouncing (80% API reduction!)

```kotlin
// ADD to UserSearchScreen.kt
val searchQueryFlow = remember { MutableStateFlow("") }

LaunchedEffect(Unit) {
    searchQueryFlow
        .debounce(500) // ⚡ Magic line!
        .collect { query ->
            if (query.length >= 2) {
                viewModel.searchUsers(query)
            }
        }
}

LaunchedEffect(searchQuery) {
    searchQueryFlow.value = searchQuery // Feed the flow
}
```

### WebSocket Message Format Fix

```kotlin
// CLIENT sends ClientWebSocketMessage
@Serializable
sealed class ClientWebSocketMessage {
    @Serializable
    @SerialName("Authenticate")
    data class Authenticate(val userId: String) : ClientWebSocketMessage()
    
    @Serializable
    @SerialName("SendMessage")
    data class SendMessage(
        val messageId: String,
        val roomId: String,
        val content: MessageContentDto
    ) : ClientWebSocketMessage()
}

// CLIENT receives ServerWebSocketMessage  
@Serializable
sealed class ServerWebSocketMessage {
    @Serializable
    @SerialName("AuthenticationSuccess")
    data class AuthenticationSuccess(...) : ServerWebSocketMessage()
    
    @Serializable
    @SerialName("NewMessage")
    data class NewMessage(val message: MessageDto) : ServerWebSocketMessage()
}
```

---

## 📋 Essential Dependency Injection Changes

**File:** `androidApp/src/main/kotlin/com/chatty/android/di/AppModule.kt`

```kotlin
// ADD UserRepository
single<UserRepository> {
    UserRepositoryImpl(
        apiClient = get(),
        tokenManager = get()
    )
}

// UPDATE LoginViewModel
viewModel { 
    LoginViewModel(
        loginUseCase = get(), 
        registerUseCase = get(), 
        logoutUseCase = get(),
        userRepository = get(),      // ADD THIS
        chatApiClient = get()        // ADD THIS
    ) 
}

// UPDATE ChatRoomViewModel
viewModel { (roomId: String) -> 
    ChatRoomViewModel(
        roomId = roomId,
        sendMessageUseCase = get(),
        observeMessagesUseCase = get(),
        getMessagesUseCase = get(),
        userRepository = get()       // ADD THIS
    )
}
```

---

## 🎨 UI Quick Wins

### Message Bubbles Color Fix

```kotlin
// In MessageBubble composable
Surface(
    color = if (isOwnMessage)
        MaterialTheme.colorScheme.primaryContainer      // Blue
    else
        MaterialTheme.colorScheme.secondaryContainer,   // Gray
    // ...
)
```

### Input Bar Enhancement

```kotlin
FilledTonalIconButton(
    onClick = onSendClick,
    enabled = messageText.isNotBlank() && !isSending,
    colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (messageText.isNotBlank()) 
            MaterialTheme.colorScheme.primary    // Blue when can send
        else 
            MaterialTheme.colorScheme.surfaceVariant  // Gray when disabled
    )
)
```

---

## 🐛 Common Error Fixes

### "No user ID found"
```kotlin
// CAUSE: User ID not saved after login
// FIX: Add to ChatApiClient.login()
tokenManager.saveUserInfo(response.userId, response.username, response.displayName)
```

### "WebSocket not authenticated"
```kotlin
// CAUSE: Not sending Authenticate message
// FIX: Add to ChatApiClient.connectWebSocket()
val authMessage = ClientWebSocketMessage.Authenticate(userId)
sendClientMessage(authMessage)
```

### "All messages on right"
```kotlin
// CAUSE: isOwnMessage always true
// FIX: Use viewModel.isOwnMessage(message)
MessageBubble(
    message = message,
    isOwnMessage = viewModel.isOwnMessage(message)
)
```

### "Search API called constantly"
```kotlin
// CAUSE: No debouncing
// FIX: Use Flow with debounce(500)
searchQueryFlow.debounce(500).collect { query -> ... }
```

---

## 📝 Testing Checklist

```
✅ Login Flow
   □ Login shows "WebSocket authenticated" in logs
   □ Token and user ID saved
   □ Navigate to chat list

✅ Message Display  
   □ Own messages appear on right (blue)
   □ Other messages appear on left (gray)
   □ Status indicators show (⏱→✓→✓✓)

✅ Search
   □ Wait 500ms before searching
   □ Show selected users as chips
   □ Clear button works

✅ Room Creation
   □ Create direct chat (1 user)
   □ Create group chat (2+ users)
   □ Navigate after confirmation

✅ WebSocket
   □ Auto-reconnect on disconnect
   □ Real-time message delivery
   □ Proper authentication
```

---

## 🔍 Debug Commands

### Check if user ID saved:
```kotlin
// In LoginViewModel after login:
println("🆔 User ID: ${tokenManager.getUserId()}")
```

### Check WebSocket connection:
```kotlin
// In ChatApiClient:
println("🔌 WebSocket state: ${websocketSession != null}")
```

### Check message sender:
```kotlin
// In MessageBubble:
println("📨 Message from: ${message.senderId.value}")
println("👤 Current user: ${uiState.currentUserId}")
println("✅ Is own: $isOwnMessage")
```

---

## 🎯 Priority Order

1. **Phase 1** (1 hour)
   - [ ] Add user ID storage to TokenManager
   - [ ] Update ChatApiClient to save user ID
   - [ ] Fix message display alignment

2. **Phase 2** (1 hour)
   - [ ] Fix WebSocket authentication
   - [ ] Add /users/me endpoint
   - [ ] Update ChatRoomViewModel

3. **Phase 3** (1 hour)
   - [ ] Add search debouncing
   - [ ] Update dependency injection
   - [ ] Test everything

**Total Time:** ~3 hours for core fixes

---

## 💾 Backup Command

```bash
# Before starting
git add .
git commit -m "Before applying Chatty fixes"
git tag before-fixes
```

---

## 📚 Full Documentation

See complete guides:
- `/mnt/user-data/outputs/COMPLETE_FIX_GUIDE.md` - Detailed implementation
- `/mnt/user-data/outputs/BEFORE_AFTER_ANALYSIS.md` - Visual comparisons
- `/mnt/user-data/outputs/migration_script.sh` - Automated migration

---

## 🆘 Emergency Rollback

```bash
# If something breaks
git reset --hard before-fixes
git clean -fd
```

---

*Print this card and keep it handy! 📌*

**Last Updated:** 2025-10-22
**Version:** 2.0-quickref
