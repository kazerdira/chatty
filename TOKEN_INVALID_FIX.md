# 🚨 TOKEN_INVALID Error - Quick Fix

## The Problem

```
❌ API call failed: TOKEN_INVALID
Message: Invalid or expired token
```

Your app has an **old saved token** that the server no longer recognizes.

## Why This Happens

1. Server was restarted (tokens cleared)
2. Token expired (past its lifetime)
3. Database was reset
4. You're testing with an old build

## The Solution

### Option 1: Clear App Data (Fastest) ⚡
```bash
adb shell pm clear com.chatty.android
```

### Option 2: Uninstall/Reinstall
1. Uninstall app from device
2. Reinstall: `.\gradlew.bat :androidApp:installDebug`
3. Login fresh

### Option 3: Clear from Settings
1. Android Settings → Apps → Chatty
2. Storage → Clear Data
3. Open app → Login

## After Clearing Data

1. **Start server:**
   ```powershell
   .\run.ps1 server
   ```

2. **Open app and login**

3. **Expected logs:**
   ```
   ✅ Login successful
   🔌 WebSocket: Connecting...
   ✅ WebSocket: Connected successfully
   🔍 WebSocket: Checking authentication - userId: user123
   🔐 WebSocket: Sent authentication for user: user123
   ```

4. **Create room and send messages** - should work! ✅

## How to Verify Token is Valid

Check the logs - you should see:
- ✅ No "TOKEN_INVALID" errors
- ✅ "WebSocket: Connected successfully"
- ✅ "Sent authentication for user: userXXX"

If you still see TOKEN_INVALID:
1. Make sure server is running
2. Make sure you're logging in (not using cached token)
3. Check server logs for authentication errors

## Prevention

In production, the app should:
1. Detect TOKEN_INVALID response
2. Automatically logout user
3. Redirect to login screen
4. Show message: "Session expired, please login again"

(This auto-logout feature will be added later)

---

**TL;DR:** Run `adb shell pm clear com.chatty.android` and login again! 🚀
