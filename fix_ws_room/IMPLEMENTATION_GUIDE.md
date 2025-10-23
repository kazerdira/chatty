# Chatty App - Implementation Guide & Testing Checklist

## 🎯 Quick Implementation Steps

### Step 1: Apply Core Fixes (5 minutes)

Replace these files in your project:

1. **shared/src/commonMain/kotlin/com/chatty/data/repository/AuthRepositoryImpl.kt**
   - Copy from: `/home/claude/AuthRepositoryImpl.kt`
   - Key change: Added 500ms delay after token saving

2. **androidApp/src/main/kotlin/com/chatty/android/ui/chat/UserSearchViewModel.kt**
   - Copy from: `/home/claude/UserSearchViewModel.kt`
   - Key changes: Added WebSocket connection check, better error handling

3. **androidApp/src/main/kotlin/com/chatty/android/di/AppModule.kt**
   - Copy from: `/home/claude/AppModule.kt`
   - Key change: Added ChatApiClient to UserSearchViewModel injection

4. **androidApp/src/main/kotlin/com/chatty/android/ui/chat/ChatListViewModel.kt**
   - Copy from: `/home/claude/ChatListViewModel.kt`
   - Key change: Wait for WebSocket before loading rooms

5. **shared/src/commonMain/kotlin/com/chatty/data/repository/ChatRoomRepositoryImpl.kt**
   - Copy from: `/home/claude/ChatRoomRepositoryImpl.kt`
   - Key change: Better room sync and refresh logic

### Step 2: Update ChatApiClient.kt (2 minutes)

In `shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt`:

**Replace** the entire `connectWebSocket()` function with the version in:
`/home/claude/ChatApiClient_connectWebSocket_FIX.kt`

Key changes:
- Added 300ms delay before connection
- Added userId and token validation
- Better error messages

### Step 3: Add Missing Import (1 minute)

In `androidApp/src/main/kotlin/com/chatty/android/ui/chat/UserSearchViewModel.kt`, make sure you have these imports:

```kotlin
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.WebSocketConnectionState
import kotlinx.coroutines.withTimeoutOrNull
```

---

## 🧪 Testing Checklist

### Test 1: Registration & Token Persistence ✅

**Steps:**
1. Clear app data/cache
2. Register a new user (e.g., "testuser123")
3. Immediately try to search for other users

**Expected Behavior:**
- ✅ Registration succeeds
- ✅ Console shows: "Tokens saved after registration"
- ✅ Console shows: "WebSocket: Connected successfully"
- ✅ Search works without "invalid token" error

**Success Criteria:** No invalid token errors

---

### Test 2: Room Creation & Sync ✅

**Prerequisites:** Two devices/emulators with different users logged in

**Steps:**
1. User A: Search for User B
2. User A: Select User B and create a room "Test Chat"
3. Wait 2-3 seconds

**Expected Behavior - User A:**
- ✅ Creating dialog appears
- ✅ Console shows: "WebSocket connected, creating room..."
- ✅ Console shows: "Room created successfully"
- ✅ Room appears in User A's chat list immediately

**Expected Behavior - User B:**
- ✅ Console shows: "New room received via WebSocket: Test Chat"
- ✅ Room appears in User B's chat list within 1-2 seconds
- ✅ No refresh needed

**Success Criteria:** Both users see the room within 2 seconds

---

### Test 3: WebSocket Resilience ✅

**Steps:**
1. Login successfully
2. Check console for "WebSocket: Connected"
3. Put app in background for 1 minute
4. Bring app to foreground
5. Try to create a room

**Expected Behavior:**
- ✅ WebSocket auto-reconnects (console shows "Reconnecting...")
- ✅ Room creation waits for WebSocket if needed
- ✅ Room creation succeeds

**Success Criteria:** No errors, room created successfully

---

### Test 4: Multiple Users Group Chat ✅

**Prerequisites:** Three users logged in on different devices

**Steps:**
1. User A: Search and select User B and User C
2. User A: Create "Group Chat Test"
3. Observe all three devices

**Expected Behavior:**
- ✅ User A sees room immediately
- ✅ User B receives WebSocket notification within 2 seconds
- ✅ User C receives WebSocket notification within 2 seconds
- ✅ All users see the same room with 3 participants

**Success Criteria:** All users synchronized within 2-3 seconds

---

### Test 5: Offline to Online Transition ✅

**Steps:**
1. Login successfully
2. Turn off WiFi/mobile data
3. Wait 10 seconds
4. Turn on WiFi/mobile data
5. Try to search for users

**Expected Behavior:**
- ✅ Console shows WebSocket reconnection attempts
- ✅ WebSocket eventually connects (within 30 seconds)
- ✅ Search works after reconnection

**Success Criteria:** App recovers automatically

---

## 🐛 Common Issues & Solutions

### Issue: "No user ID found" in logs

**Solution:** User logged in before the fix was applied.
**Fix:** Logout and login again to save user ID properly.

**Code location:** 
```kotlin
// In AuthRepositoryImpl.kt
tokenManager.saveUserId(response.userId) // This line saves the user ID
```

---

### Issue: Room spinning forever

**Possible Causes:**
1. WebSocket not connected
2. Server not responding
3. Token expired

**Debug Steps:**
1. Check console for "WebSocket: Connected successfully"
2. If not connected, check server is running
3. Try logout and login again

**Logs to look for:**
```
✅ WebSocket: Connected successfully
✅ UserSearchViewModel: WebSocket connected, creating room...
✅ Room created successfully
```

---

### Issue: Other users don't see new room

**Possible Causes:**
1. WebSocket not connected on other devices
2. Server not broadcasting properly
3. Network issues

**Debug Steps:**
1. On other devices, check console for "New room received via WebSocket"
2. Check server logs for "Notifying X users about new room"
3. Try manual refresh (pull down on chat list)

**Server Log to look for:**
```
📢 Notifying 2 users about new room: Test Chat
```

---

## 📊 Console Log Guide

### Successful Flow Logs:

**Registration:**
```
💾 AuthRepository: Saving tokens after registration for user: abc-123
✅ AuthRepository: Tokens saved after registration for user: abc-123
🔌 AuthRepository: Connecting WebSocket...
🔌 WebSocket: Connecting... (attempt 1)
🔐 WebSocket: Connecting with userId: abc-123, token: eyJhbGciOiJIUzI1...
✅ WebSocket: Connected successfully
🔐 WebSocket: Authentication message sent for user: abc-123
```

**Room Creation:**
```
🔌 UserSearchViewModel: Checking WebSocket connection...
✅ UserSearchViewModel: WebSocket connected, creating room...
📝 UserSearchViewModel: Creating GROUP room with 2 participants
📝 ChatRoomRepository: Creating room 'Test Chat' of type GROUP
✅ ChatRoomRepository: Room created successfully: room-xyz-789
✅ UserSearchViewModel: Room created successfully: room-xyz-789
```

**Room Reception (other users):**
```
📨 WebSocket: Received message: NewRoom
✅ ChatRoomRepository: New room received via WebSocket: Test Chat
📋 ChatListViewModel: Received 3 rooms
```

---

## 🎉 Success Indicators

When everything is working correctly, you should see:

1. ✅ **No more invalid token errors** after registration
2. ✅ **Rooms appear instantly** for creator
3. ✅ **Rooms appear within 2 seconds** for other users
4. ✅ **No infinite spinning** during room creation
5. ✅ **WebSocket reconnects automatically** after network issues
6. ✅ **Clear error messages** when things go wrong

---

## 🔧 Advanced Debugging

### Enable Verbose Logging

Already enabled in the fixed code! Look for these emoji prefixes:

- 💾 = Token operations
- 🔌 = WebSocket operations
- ✅ = Success
- ❌ = Error
- ⚠️ = Warning
- 📝 = Room operations
- 📨 = WebSocket messages
- 🔄 = Refresh operations
- 📋 = UI state updates

### Check Server Logs

On the server side, you should see:

```
🔌 WebSocket: User abc-123 connected (1 sessions)
📍 WebSocket: User abc-123 joined room room-xyz-789
📢 Broadcasting to room room-xyz-789: 2 participants
📢 Notifying 2 users about new room: Test Chat
```

### Network Traffic Monitoring

Use Android Studio's Network Profiler to verify:

1. POST `/auth/register` - Returns 201 with token
2. WS `/ws` - WebSocket connection established
3. POST `/rooms` - Returns 201 with new room
4. GET `/rooms` - Returns list of rooms

---

## 📝 Performance Metrics

After these fixes, you should see:

- **Token save time:** ~200-500ms
- **WebSocket connect time:** ~500-1000ms
- **Room creation time:** ~300-800ms
- **Room sync time (other users):** ~500-2000ms
- **Total registration to ready:** ~1.5-3 seconds

---

## 🚀 Next Steps After Testing

Once all tests pass:

1. **Commit the changes** to version control
2. **Document the fixes** in your change log
3. **Test on real devices** (not just emulator)
4. **Test with poor network conditions**
5. **Add analytics** to track WebSocket connection rates
6. **Consider adding user feedback**:
   - "Connecting..." indicator
   - "Room created" toast message
   - "Reconnecting..." status in chat list

---

## 💡 Pro Tips

1. **Always check WebSocket status** before real-time operations
2. **Add retry logic** for all network operations
3. **Give tokens time to persist** on mobile devices (encrypted storage is slower)
4. **Use exponential backoff** for reconnection attempts
5. **Log everything** during development (you can remove later)
6. **Test on slow networks** to catch race conditions
7. **Clear app data** between major test runs

---

## 📞 Support

If you still encounter issues after applying these fixes:

1. Check all files were replaced correctly
2. Clean and rebuild the project
3. Clear app data and test from scratch
4. Verify server is running and accessible
5. Check server logs for errors
6. Review console logs for the error patterns listed above

**Most Common Fix:** Logout and login again after updating the code!

