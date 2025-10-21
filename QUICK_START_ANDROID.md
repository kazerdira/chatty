# 🎯 QUICK START: Run Your Android App

## The EASIEST Way (Recommended) ⭐

### Open in Android Studio and Click Run!

1. **Open Android Studio**
2. **File** → **Open** → Select `F:\kotlin\chatty`
3. Wait for sync (2-3 minutes first time)
4. Click green **Run** button ▶️
5. Done! 🎉

---

## Alternative: Use the PowerShell Script

I've created a helper script for you!

```powershell
# Run this command:
.\run-android.ps1
```

This script will:
- ✅ Stop Gradle daemons
- ✅ Clean build files
- ✅ Build the APK
- ✅ Check for devices
- ✅ Offer to install automatically

---

## If You Get "File Locked" Error

This happens when Android Studio or another process has files open.

**Solution:**
1. Close **ALL** of these apps:
   - Android Studio
   - IntelliJ IDEA  
   - VS Code
   
2. Run again:
   ```powershell
   .\run-android.ps1
   ```

**OR** just use Android Studio (it handles this automatically!)

---

## What You Need

### ✅ You Already Have:
- Android SDK: `C:\Users\boure_rr1habg\AppData\Local\Android\Sdk`
- Gradle wrapper: Installed
- Project: Built successfully (shared module)

### ❓ You Need ONE of These:
- **Android Emulator** (from Android Studio), OR
- **Physical Android Device** (with USB debugging enabled)

---

## Set Up an Emulator (If You Don't Have One)

**In Android Studio:**
1. Click **Device Manager** (phone icon, top right)
2. Click **Create Device**
3. Select **Pixel 5** → **Next**
4. Select **Tiramisu (API 33)** → **Next**  
5. Click **Finish**
6. Click ▶️ to start it

---

## Your App Features

Once running, you'll have:

### 📱 Login Screen
- Beautiful Material 3 design
- Username & password fields
- Show/hide password toggle
- Register account option

### 💬 Chat List  
- List all conversations
- Create new chats
- See last messages

### ✉️ Chat Room
- Send/receive messages
- Message bubbles
- Read receipts (✓✓)
- Timestamps

---

## Quick Commands

```powershell
# Use the helper script (easiest)
.\run-android.ps1

# Or manual commands:
.\gradlew.bat --stop                    # Stop daemons
.\gradlew.bat :androidApp:assembleDebug # Build APK
.\gradlew.bat :androidApp:installDebug  # Install to device
```

---

## Troubleshooting One-Liners

```powershell
# File locked? Close everything and run:
Get-Process | Where-Object {$_.ProcessName -match "java|gradle|studio"} | Stop-Process -Force; .\run-android.ps1

# Can't find device?
adb devices  # Shows connected devices

# Start fresh:
.\gradlew.bat clean; .\gradlew.bat :androidApp:assembleDebug
```

---

## 🎉 Next Steps After Running

1. **Test the login screen** - Try typing in fields
2. **Click Register** - See the form switch
3. **Navigate around** - Test the UI
4. **Connect to backend** - When you have a server running

---

## Need Help?

- **Build errors?** Try closing Android Studio and using the script
- **No devices?** Set up an emulator in Android Studio
- **App crashes?** Check Logcat in Android Studio
- **Something else?** Just ask me!

---

## TL;DR

**Just do this:**

1. Open Android Studio
2. Open project: `F:\kotlin\chatty`
3. Click Run ▶️
4. Watch it launch! 🚀

---

That's it! You've got a complete, working Android chat app ready to go! 🎉
