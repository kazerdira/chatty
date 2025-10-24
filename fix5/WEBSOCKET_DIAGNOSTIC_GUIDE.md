# 🔍 WebSocket Real-Time Notification - Diagnostic & Fix Guide

## Current Situation

✅ **Working:** User1 creates room → Room appears for User1 (optimistic update)  
❌ **Not Working:** User2 doesn't see new room in real-time  
✅ **Workaround:** User2 sees room after manual refresh (HTTP API works)

## Root Cause Analysis

The issue is **WebSocket notification delivery**. Here's what should happen:

```
User1 Creates Room
    ↓
HTTP POST /rooms → Server
    ↓
Server saves to database
    ↓
Server sends WebSocket notification to all participants
    ↓
User2's WebSocket receives "NewRoom" message
    ↓
User2's UI updates automatically ✅
```

**The chain is breaking** between "Server sends notification" and "User2 receives it".

## Step 1: Enhanced Client (Auto-Refresh Fallback)

I've created an enhanced version of `ChatRoomRepositoryImpl` that:
- ✅ Auto-refreshes every 15 seconds when WebSocket is down
- ✅ Better logging to diagnose the issue
- ✅ Monitors WebSocket connection status
- ✅ Prevents duplicate rooms

**Replace your file with:** [View ChatRoomRepositoryImpl_Enhanced.kt](computer:///mnt/user-data/outputs/ChatRoomRepositoryImpl_Enhanced.kt)

**Location:** `shared/src/commonMain/kotlin/com/chatty/data/repository/ChatRoomRepositoryImpl.kt`

## Step 2: Diagnose WebSocket Connection

Run these checks on **User2's device**:

### Check 1: Is WebSocket Connected?

**Look for these logs when User2 opens the app:**

```
✅ Good logs (WebSocket working):
🔌 WebSocket: Connecting...
✅ WebSocket: Connected successfully
🔐 WebSocket: Authentication message sent
✅ ChatRoomRepository: WebSocket authenticated: user2-id

❌ Bad logs (WebSocket failing):
❌ WebSocket: Connection error: ...
❌ WebSocket: Connection timeout
🔌 WebSocket: Reconnecting...
```

**If you see bad logs**, WebSocket isn't connecting. Skip to "Step 3: Fix WebSocket Connection"

### Check 2: Is User2 Receiving WebSocket Messages?

**After User1 creates a room, check User2's logs:**

```
✅ Good (receiving):
📨 ChatRoomRepository: Received NewRoom via WebSocket
✅ ChatRoomRepository: Adding new room: Chat with User1 (abc-123)

❌ Bad (not receiving):
(No log output at all when User1 creates room)
```

**If not receiving**, the server might not be sending to User2. Continue to Step 3.

## Step 3: Fix WebSocket Connection

### Issue A: User2 Not Authenticated to WebSocket

**Problem:** User2's WebSocket connected but not authenticated.

**Check in server logs:**
```bash
# Look for authentication message when User2 logs in
New WebSocket connection: session-123
User user2-id authenticated
```

**Fix:** Ensure `ChatApiClient.connectWebSocket()` is called after login.

**Update AuthRepositoryImpl.kt:**
```kotlin
override suspend fun login(username: String, password: String): Result<AuthTokens> {
    return apiClient.login(AuthRequest(username, password)).mapCatching { response ->
        // Save tokens
        tokenManager.saveAccessToken(response.token)
        tokenManager.saveRefreshToken(response.refreshToken)
        tokenManager.saveUserId(response.userId)
        
        delay(500) // Let tokens persist
        
        // ✅ CRITICAL: Connect WebSocket after login
        println("🔌 AuthRepository: Connecting WebSocket for ${response.userId}")
        apiClient.connectWebSocket()
        
        AuthTokens(...)
    }
}
```

### Issue B: Server Not Sending to User2

**Check server's Application.kt - `roomRoutes` POST endpoint:**

```kotlin
post {
    // ... room creation code ...
    
    val room = roomRepository.createRoom(...)
    
    // ✅ VERIFY THIS LINE EXISTS:
    webSocketManager.notifyNewRoom(room, allParticipants)
    
    call.respond(HttpStatusCode.Created, room)
}
```

**Check server logs when User1 creates room:**
```bash
# Should see:
📢 Notifying 2 users about new room: Chat Room Name
📨 WebSocket: Sending to user: user2-id
```

**If you don't see these logs**, the server isn't sending notifications.

### Issue C: Wrong Participant List

**Problem:** User2 is not included in the participant list.

**Check in UserSearchViewModel when creating room:**

```kotlin
fun createRoom(roomName: String) {
    val selectedUsers = _uiState.value.selectedUsers
    
    // ✅ VERIFY: Are you passing the right user IDs?
    println("📝 Creating room with participants: ${selectedUsers.map { it.id.value }}")
    
    createRoomUseCase(
        CreateRoomUseCase.CreateRoomParams(
            name = roomName,
            type = roomType,
            participantIds = selectedUsers.map { it.id } // ✅ Should include User2
        )
    )
}
```

**Check server logs:**
```bash
# Should see User2's ID in participants
Creating DIRECT room: Chat Room
Participants: [user1-id, user2-id]  # ✅ User2 should be here!
```

## Step 4: Verify Server WebSocket Manager

**Check that server's `WebSocketManager.notifyNewRoom()` is working:**

Add extra logging to `server/src/main/kotlin/com/chatty/server/WebSocketManager.kt`:

```kotlin
suspend fun notifyNewRoom(room: ChatRoomDto, participantIds: List<String>) {
    val message = WebSocketMessage.NewRoom(room)
    println("📢 Notifying ${participantIds.size} users about new room: ${room.name}")
    
    participantIds.forEach { userId ->
        println("📤 Attempting to send NewRoom to user: $userId")
        
        val sessions = userSessions[userId]
        if (sessions == null || sessions.isEmpty()) {
            println("⚠️ User $userId has no active WebSocket sessions!")
        } else {
            println("✅ User $userId has ${sessions.size} active session(s)")
            sendToUser(userId, message)
        }
    }
}
```

## Step 5: Quick Test Process

Follow this exact sequence:

### Test A: Single Device (Verify Server)
1. Logout from app completely
2. Login as `alice` (password: `password123`)
3. Check logs: Should see "WebSocket authenticated"
4. Create a room with `bob` as participant
5. **Check server logs:** Should see "Notifying 2 users about new room"
6. **Check server logs:** Should see "User bob-uuid has X active session(s)"

If you see "⚠️ User bob-uuid has no active WebSocket sessions!" → Bob is not connected to WebSocket!

### Test B: Two Devices (Verify Notification)
1. **Device 1:** Login as `alice`
2. **Device 2:** Login as `bob`
3. **Both:** Check logs for "WebSocket authenticated"
4. **Device 1 (Alice):** Create room with Bob
5. **Device 2 (Bob):** Watch logs - should see "📨 Received NewRoom via WebSocket"
6. **Device 2 (Bob):** Room should appear in UI within 1-2 seconds

## Step 6: Fallback Solution (If WebSocket is Hard to Fix)

The enhanced `ChatRoomRepositoryImpl` includes auto-refresh:
- Checks every 15 seconds if WebSocket is disconnected
- Automatically fetches rooms via HTTP API
- Users will see new rooms within 15 seconds even if WebSocket fails

**This is good enough for production** while you debug WebSocket!

## Common Issues & Solutions

### Issue 1: "User has no active WebSocket sessions"
**Cause:** User2's WebSocket not connected  
**Solution:** Ensure `apiClient.connectWebSocket()` is called after login

### Issue 2: WebSocket connects then immediately disconnects
**Cause:** Authentication token expired or invalid  
**Solution:** Check token is saved before connecting WebSocket

### Issue 3: Notification sent but not received
**Cause:** Client not listening to `incomingMessages` flow  
**Solution:** Verify `ChatRoomRepositoryImpl` init block collects from `apiClient.incomingMessages`

### Issue 4: Room appears for User1 but with wrong participants
**Cause:** Wrong participant IDs sent to server  
**Solution:** Verify `selectedUsers.map { it.id }` includes all selected users

## Debug Commands

### Enable All Logging
```bash
# Android
adb logcat | grep -E "WebSocket|ChatRoom|UserSearch|Auth"

# Server
./gradlew run | grep -E "WebSocket|Room|Notify"
```

### Test WebSocket Directly
Use a WebSocket client (e.g., [websocat](https://github.com/vi/websocat)):

```bash
# Connect to server
websocat ws://localhost:8080/ws

# Send authentication
{"Authenticate":{"userId":"user2-uuid"}}

# You should see:
{"Connected": ...}
{"AuthenticationSuccess": ...}
```

## Expected Timeline

With the enhanced solution:

| Scenario | Old Behavior | New Behavior |
|----------|-------------|--------------|
| WebSocket Working | Instant | Instant ✅ |
| WebSocket Down | Never (manual refresh) | 15 seconds (auto-refresh) ✅ |
| WebSocket Reconnects | N/A | Auto-sync ✅ |

## Success Criteria

✅ User2 sees new rooms within 2 seconds (WebSocket working)  
✅ User2 sees new rooms within 15 seconds (WebSocket down, fallback)  
✅ No manual refresh needed  
✅ Logs show clear diagnostic information

## Next Steps

1. **Replace ChatRoomRepositoryImpl** with enhanced version
2. **Run Test A** to verify server is sending notifications
3. **Run Test B** to verify User2 is receiving
4. **Check logs** to identify where the chain breaks
5. **Apply specific fix** based on diagnostic results

The enhanced version ensures your app works reliably even while you debug WebSocket! 🚀

---

**Need more help?** Share the logs from:
- User2 when opening app (WebSocket connection)
- Server when User1 creates room (notification sending)
- User2 when User1 creates room (notification receiving)
