# 🚀 Quick Start Guide - Chatty Full Stack App

## Your Complete App is Ready!

### ✅ What's Done
- **Android App**: Fully built and ready to run
- **Backend Server**: All APIs implemented and tested
- **Authentication**: JWT-based login/register working
- **Mock Data**: Users and chat rooms ready for testing

---

## 🎯 Run the Full Stack (5 Minutes)

### Step 1: Start the Backend Server (1 minute)

```powershell
# In PowerShell, from F:\kotlin\chatty
.\gradlew.bat :server:run
```

**✅ Server running on**: http://localhost:8080

**Verify it's working**:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/health" -Method Get
```

Should show: `status: healthy`

---

### Step 2: Run the Android App (4 minutes)

#### Option A: Android Studio (Recommended)
1. Open Android Studio
2. Open project: `F:\kotlin\chatty`
3. Wait for Gradle sync
4. Click ▶️ Run button
5. Select emulator or device

#### Option B: Command Line
```powershell
# Build the APK
.\gradlew.bat :androidApp:assembleDebug

# Install on connected device
adb install androidApp\build\outputs\apk\debug\androidApp-debug.apk

# Launch the app
adb shell am start -n com.chatty.android/.MainActivity
```

---

### Step 3: Test the App (1 minute)

1. **Login Screen** appears
   - Username: `alice`
   - Password: `password123`
   - Click "Sign In"

2. **Chat List** appears
   - See "General Chat" room
   - Shows last message from Bob

3. **Open Chat Room**
   - Click "General Chat"
   - See 2 existing messages
   - Type and send a new message

4. **Success!** 🎉
   - Your message appears
   - Backend receives it
   - Full stack working!

---

## 🧪 Quick API Tests (Optional)

### Test Login
```powershell
$body = @{username='alice'; password='password123'} | ConvertTo-Json
$response = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -Body $body -ContentType "application/json"
$response
```

### Get Chat Rooms
```powershell
$token = $response.token
$headers = @{Authorization="Bearer $token"}
Invoke-RestMethod -Uri "http://localhost:8080/rooms" -Method Get -Headers $headers
```

### Get Messages
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/messages?roomId=room-1" -Method Get -Headers $headers
```

---

## 👥 Test Users

### Pre-created Users (Mock Data)
- **Alice**: `alice` / `password123`
- **Bob**: `bob` / `password123`

### Create New User
Use the Register tab in the app, or:
```powershell
$newUser = @{
    username='charlie'
    password='test123'
    displayName='Charlie Brown'
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/auth/register" -Method Post -Body $newUser -ContentType "application/json"
```

---

## 💡 What You Can Do Now

### ✅ Working Features
- ✅ User login/register
- ✅ View chat rooms
- ✅ View message history
- ✅ Send messages
- ✅ Real-time UI updates
- ✅ JWT authentication
- ✅ Material 3 design

### 🔄 Future Enhancements (Optional)
- Add PostgreSQL database
- WebSocket real-time messaging
- Message editing/deletion
- File uploads
- Push notifications
- Group management
- Desktop app
- iOS app

---

## 🐛 Troubleshooting

### Backend won't start
```powershell
# Kill any running server
.\gradlew.bat --stop

# Clean and rebuild
.\gradlew.bat clean :server:build

# Start again
.\gradlew.bat :server:run
```

### Android build fails
```powershell
# Stop Gradle daemons
.\gradlew.bat --stop

# Clean build
.\gradlew.bat clean :androidApp:assembleDebug
```

### Can't login
- Check backend is running: http://localhost:8080/health
- Use mock credentials: `alice` / `password123`
- Check terminal for error messages

---

## 📂 Project Structure

```
chatty/
├── androidApp/          # ✅ Android app (Jetpack Compose)
├── server/             # ✅ Backend server (Ktor)
├── shared/             # ✅ Shared code (Domain + Data)
├── gradle/             # Build configuration
└── *.md               # Documentation
```

---

## 🎉 Success Criteria

Your app is working if:
- ✅ Backend responds to health check
- ✅ Login returns JWT token
- ✅ Android app shows login screen
- ✅ Can login as Alice
- ✅ Chat list shows "General Chat"
- ✅ Can open chat and see 2 messages
- ✅ Can send new messages

---

## 📞 Need Help?

Check these files:
- `BACKEND_COMPLETE.md` - Full API documentation
- `BUILD_SUCCESS_ANDROID.md` - Android build guide
- `COMPLIANCE_CHECK.md` - Feature comparison

---

**🚀 Your full-stack chat app is ready to go! Enjoy!**

Last Updated: October 21, 2025
