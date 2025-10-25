# 🚀 Quick Implementation Guide

## Problem
❌ Messages failing with: "Fields [id, roomId, senderId, senderName, content, timestamp, status] are required"

## Root Cause
Client's `MessageDto` expects `senderAvatar` field that server doesn't return

## Solution (3 Steps)

### Step 1: Replace MessageDto.kt ⏱️ 2 mins

**Location**: `shared/src/commonMain/kotlin/com/chatty/data/remote/dto/MessageDto.kt`

**Change**: Remove `senderAvatar` field

```kotlin
// OLD (BROKEN):
data class MessageDto(
    val id: String,
    val senderAvatar: String? = null,  // ❌ Server doesn't return this
    ...
)

// NEW (FIXED):
data class MessageDto(
    val id: String,
    // senderAvatar removed
    ...
)
```

➡️ **Use the fixed file**: `MessageDto.kt` in outputs folder

---

### Step 2: Clean & Rebuild ⏱️ 2 mins

```bash
# In Android Studio
1. Build → Clean Project
2. Build → Rebuild Project
3. Run app
```

---

### Step 3: Test Messaging ⏱️ 1 min

```
1. Login as alice (password: password123)
2. Create new chat with bob
3. Send message: "Hello from fixed app!"
4. Check logs for: "✅ HTTP: Message sent successfully"
```

---

## Expected Results

### ✅ Before Fix
```
❌ API call failed: Fields [...] are required
❌ OutboxProcessor: Message failed
📤 OutboxProcessor: Processing 1 pending messages (stuck forever)
```

### ✅ After Fix
```
📤 HTTP: Sending message to room abc-123
✅ HTTP: Message sent successfully: msg-456
✅ OutboxProcessor: Message sent successfully
📊 OutboxProcessor: Batch complete - ✅ 1 sent, ❌ 0 failed
```

---

## Why This Happened

### Server Response (Reality)
```json
{
  "id": "msg-123",
  "roomId": "room-456",
  "senderId": "user-789",
  "senderName": "Alice",
  "content": {...},
  "timestamp": "2025-10-25T20:13:31Z",
  "status": "SENT"
}
```

### Client Expected (Wrong)
```json
{
  "id": "msg-123",
  "senderAvatar": "https://...",  // ❌ NOT PRESENT IN SERVER RESPONSE
  ...
}
```

### Result
- JSON deserialization fails
- Kotlin throws exception
- Message stuck in outbox
- User sees "sending..." forever

---

## Business Logic Behind The Fix

### Outbox Pattern (Guaranteed Delivery)
```
User sends message
    ↓
Save to local DB (outbox)
    ↓
Background worker tries to send
    ↓
Retry with exponential backoff (1s, 2s, 4s, 8s, 16s)
    ↓
After 5 attempts: Manual retry required
```

### Why HTTP API (Not WebSocket)
- ✅ **Reliable**: HTTP has built-in retry mechanisms
- ✅ **Scalable**: Works with load balancers
- ✅ **Testable**: Easy to debug with curl/Postman
- ✅ **Simple**: No connection state management

### WebSocket Role
- Real-time updates for OTHER users
- Bonus feature, not critical path
- If WebSocket fails, HTTP still works

---

## Monitoring

### Check Outbox Metrics
```kotlin
// In your logs, look for:
📊 Outbox Metrics:
   Pending: 0          // Should be 0 when working
   Failed: 0           // Should stay low
   Total Processed: 5  // Increases over time
   Circuit Breaker: false
```

### Common Issues

#### Issue: "Circuit breaker active"
**Cause**: 10+ consecutive failures  
**Fix**: Check network connection, verify server is running  
**Recovery**: Automatic after 60 seconds

#### Issue: Messages still pending
**Cause**: Server not reachable  
**Fix**: Check baseUrl in AppModule.kt (should be `http://10.0.2.2:8080`)

---

## What's Next (Optional Improvements)

### 1. Add Avatar Support (Server-side)
```kotlin
// In server's MessageRepository.kt
data class MessageDto(
    ...
    val senderAvatar: String? = null,  // Add this
)
```

### 2. Add Message Editing
```kotlin
// Already supported in domain model
// Just need API endpoint
```

### 3. Add Read Receipts
```kotlin
// Track who read each message
// Update status from SENT → READ
```

---

## FAQ

**Q: Will old messages in outbox be sent?**  
A: Yes! Outbox is persistent. After fix, processor will retry all pending messages.

**Q: Do I need to clear app data?**  
A: No. The fix is backward compatible.

**Q: What about iOS?**  
A: Same fix applies - shared code is used by both platforms.

**Q: Performance impact?**  
A: Zero. Only changed data structure, no logic changes.

---

## Support

If issues persist:

1. **Check server logs**:
   ```bash
   # Look for POST /messages requests
   # Verify 201 Created response
   ```

2. **Check client logs**:
   ```bash
   # Look for "❌ API call failed"
   # Note the error message
   ```

3. **Enable verbose logging**:
   ```kotlin
   // In ChatApiClient
   install(Logging) {
       level = LogLevel.ALL
   }
   ```

---

**Status**: ✅ Ready to implement  
**Time required**: 5 minutes  
**Risk**: Low (only data structure change)  
**Impact**: Critical (fixes core messaging)
