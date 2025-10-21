# 🚀 How to Run the Chatty Android App

## ✅ Prerequisites Checklist

Before running, make sure you have:
- ✅ Android SDK installed (already detected at: `C:\Users\boure_rr1habg\AppData\Local\Android\Sdk`)
- ✅ Gradle wrapper installed (already set up)
- ✅ Java/Kotlin configured (using Java 17)

## 🎯 Quick Start - Choose Your Method

---

### **Method 1: Using Android Studio** (⭐ RECOMMENDED - Easiest!)

This is the **easiest and most reliable** way to run your Android app:

#### Steps:
1. **Open Android Studio**
   - If you don't have it: Download from https://developer.android.com/studio

2. **Open this project**
   - File → Open → Select: `F:\kotlin\chatty`
   - Wait for Gradle sync to complete (this may take 2-3 minutes the first time)

3. **Set up an emulator** (if you don't have one)
   - Click "Device Manager" (phone icon in top right)
   - Click "Create Virtual Device"
   - Select "Pixel 5" or any phone → Click "Next"
   - Select "Tiramisu" (API 33) or latest → Click "Next"
   - Click "Finish"

4. **Run the app**
   - Select your device/emulator from the dropdown (top toolbar)
   - Click the green "Run" button (▶️) or press `Shift + F10`
   - Wait for the app to build and launch!

5. **You'll see:**
   - Emulator will open (if not already running)
   - App will install automatically
   - Login screen will appear with 💬 Chatty logo!

---

### **Method 2: Command Line** (If you prefer terminal)

#### Step 1: Make sure no other processes are using the files
```powershell
# Stop all Gradle daemons
.\gradlew.bat --stop

# Wait a moment
Start-Sleep -Seconds 2
```

#### Step 2: Build the APK
```powershell
# Build the debug APK
.\gradlew.bat :androidApp:assembleDebug
```

The APK will be created at:
```
F:\kotlin\chatty\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

#### Step 3: Install on a device

**If you have an emulator running:**
```powershell
.\gradlew.bat :androidApp:installDebug
```

**Or manually install the APK:**
```powershell
# Start an emulator first (from Android Studio)
# Then drag the APK file to the emulator window
```

**If you have a physical Android device:**
1. Enable Developer Options on your phone:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging → ON
3. Connect phone via USB
4. Run: `.\gradlew.bat :androidApp:installDebug`

---

### **Method 3: Build and Run in One Command**

```powershell
# This will build, install, and launch the app
.\gradlew.bat :androidApp:installDebug

# Then manually launch the app from your device
```

---

## 🐛 Troubleshooting

### Problem: "File is locked" error
```powershell
# Solution 1: Stop all Gradle processes
.\gradlew.bat --stop

# Solution 2: Close Android Studio and VS Code
# Then retry the build

# Solution 3: Force delete build folder
Remove-Item -Path "androidApp\build" -Recurse -Force -ErrorAction SilentlyContinue
```

### Problem: "SDK not found"
```powershell
# Your SDK is already configured in local.properties:
# sdk.dir=C:\\Users\\boure_rr1habg\\AppData\\Local\\Android\\Sdk
# This should work automatically
```

### Problem: "No devices found"
```powershell
# Check connected devices
adb devices

# If empty, you need to:
# 1. Start an emulator from Android Studio, OR
# 2. Connect a physical device with USB debugging enabled
```

### Problem: Build is very slow
```powershell
# Gradle is downloading dependencies for the first time
# This is normal - subsequent builds will be much faster!
# First build: 2-5 minutes
# After that: 10-30 seconds
```

---

## 📱 What You'll See When It Runs

### 1. Login Screen
```
┌─────────────────────────┐
│                         │
│      💬 Chatty          │
│    Welcome back!        │
│                         │
│  ┌─────────────────┐   │
│  │ Username        │   │
│  └─────────────────┘   │
│                         │
│  ┌─────────────────┐   │
│  │ Password     👁  │   │
│  └─────────────────┘   │
│                         │
│  ┌─────────────────┐   │
│  │     Login       │   │
│  └─────────────────┘   │
│                         │
│  Don't have an account? │
│      Register           │
│                         │
└─────────────────────────┘
```

### 2. Chat List Screen (after login)
```
┌─────────────────────────┐
│  Chats              +   │
├─────────────────────────┤
│                         │
│   No chats yet          │
│ Start a conversation    │
│                         │
│  ┌─────────────────┐   │
│  │   New Chat      │   │
│  └─────────────────┘   │
│                         │
└─────────────────────────┘
```

### 3. Chat Room Screen
```
┌─────────────────────────┐
│ ← Chat Room             │
├─────────────────────────┤
│                         │
│         ┌───────────┐   │
│         │ Hello! 👋 │   │
│         │ 10:30  ✓✓ │   │
│         └───────────┘   │
│                         │
│  ┌───────────┐          │
│  │ Hi there! │          │
│  │ 10:31     │          │
│  └───────────┘          │
│                         │
├─────────────────────────┤
│ Type a message...    📤 │
└─────────────────────────┘
```

---

## ⚡ Quick Command Reference

```powershell
# Stop all Gradle processes
.\gradlew.bat --stop

# Clean build
.\gradlew.bat clean

# Build shared module only
.\gradlew.bat :shared:build

# Build Android app
.\gradlew.bat :androidApp:assembleDebug

# Install to device/emulator
.\gradlew.bat :androidApp:installDebug

# Run tests
.\gradlew.bat test

# See all available tasks
.\gradlew.bat tasks
```

---

## 🎉 Success Indicators

You'll know it's working when you see:

1. **Build Success:**
```
BUILD SUCCESSFUL in 45s
```

2. **APK Created:**
```
File exists: androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

3. **Install Success:**
```
Installing APK 'androidApp-debug.apk' on 'Pixel_5_API_33'
Installed on 1 device.
```

4. **App Launches:**
- Emulator shows your app icon
- App opens to the login screen
- You can type in the text fields!

---

## 💡 Pro Tips

1. **Use Android Studio** - It handles all the complexity for you
2. **Keep emulator running** - Don't close it between builds
3. **Use "Run" not "Debug"** - Debug mode is slower
4. **First build is slow** - Be patient, it's downloading libraries
5. **Save this file** - Refer back when needed!

---

## 🚀 Ready to Run?

**Recommended command right now:**

```powershell
# Try building again now that we cleared the lock:
.\gradlew.bat :androidApp:assembleDebug
```

If this works, you'll see `BUILD SUCCESSFUL` and can then run:
```powershell
.\gradlew.bat :androidApp:installDebug
```

**Or just open Android Studio and click the Run button!** ▶️

---

Good luck! 🎉 You've got this! Let me know if you hit any issues.
