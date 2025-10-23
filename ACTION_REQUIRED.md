# 🚨 URGENT: Clear App Data Required!

## Why?
Users who logged in **before Fix #6** don't have their user ID saved, so WebSocket authentication fails.

## Quick Fix (Choose One)

### Option 1: Clear App Data (Fastest)
```bash
adb shell pm clear com.chatty.android
```

### Option 2: Uninstall/Reinstall
1. Uninstall app
2. Reinstall APK
3. Login again

### Option 3: Logout Feature (If Available)
1. Go to Settings → Logout
2. Login again

## What This Does
- Deletes old tokens (without user ID)
- Forces fresh login
- New login will save user ID ✅
- WebSocket authentication will work ✅

## After Clearing Data

### Start Server
```powershell
.\run.ps1 server
```

### Install Latest Build
```powershell
.\gradlew.bat :androidApp:installDebug
```

### Test Both Users
1. **User 1:** Login → Create room → Send "Hello"
2. **User 2:** Login → Join room → Send "Hi back"

### Expected Logs (Both Users)
```
🔍 WebSocket: Checking authentication - userId from token: user123
🔐 WebSocket: Sent authentication for user: user123
🚪 Joining room: room456
📨 Received new message: msg789
```

### ❌ If You See This (Old Login)
```
🔍 WebSocket: Checking authentication - userId from token: null
❌ WebSocket: CRITICAL - No user ID found!
❌ This means user logged in before Fix #6
❌ User must logout and login again to save user ID
```
**→ You forgot to clear app data! Clear it and try again.**

## What Was Fixed

### Fix #6 (Previous)
✅ Login/register now saves user ID

### Fix #7 (New - This Build)
✅ Messages now display in correct order (oldest → newest)  
✅ Better authentication error messages  
✅ WebSocket state shows ERROR if user ID missing

## Message Order Now Correct

### Before Fix #7
```
[Top]
Message 3 (newest) ❌
Message 2
Message 1 (oldest)
[Bottom]
```

### After Fix #7
```
[Top]
Message 1 (oldest) ✅
Message 2
Message 3 (newest) ✅
[Bottom] ← New messages appear here
```

## Build Info
```
BUILD SUCCESSFUL in 6m 32s
All 7 critical fixes complete ✅
```

---

**TL;DR:** Run `adb shell pm clear com.chatty.android` on ALL test devices, then login fresh! 🚀
