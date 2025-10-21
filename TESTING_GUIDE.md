# 🧪 Testing & Installation Guide

## 📋 Prerequisites Check

Before we start, let's make sure you have everything installed.

### Required Software

1. **Java Development Kit (JDK)**
   - Version: JDK 17 or higher
   - Check: `java -version`
   - Download: https://adoptium.net/

2. **Gradle**
   - Included in project (Gradle Wrapper)
   - Check: `./gradlew --version` or `.\gradlew.bat --version` (Windows)

3. **Android Studio** (Optional - only for Android development)
   - Download: https://developer.android.com/studio
   - Includes Android SDK

4. **IntelliJ IDEA** (Recommended for Desktop)
   - Download: https://www.jetbrains.com/idea/download/
   - Community Edition is free

### Optional (for full stack)

5. **PostgreSQL** (for server - later)
   - Download: https://www.postgresql.org/download/
   - Or use Docker: `docker run -p 5432:5432 -e POSTGRES_PASSWORD=password postgres`

6. **Docker** (for easy PostgreSQL)
   - Download: https://www.docker.com/products/docker-desktop

---

## 🚀 Quick Start - Test What We Have

Let's test the shared module first to make sure everything compiles!

### Step 1: Check Your Setup

Open PowerShell in the project directory and run:

```powershell
# Check Java version
java -version
# Should show version 17 or higher

# Check Gradle wrapper
.\gradlew --version
# Should show Gradle version
```

### Step 2: Build the Shared Module

```powershell
# Build the shared module
.\gradlew :shared:build

# This will:
# - Download dependencies
# - Compile Kotlin code
# - Generate SQLDelight code
# - Run basic compilation checks
```

**Expected output**: `BUILD SUCCESSFUL` ✅

If you see errors, don't worry! We'll fix them together.

### Step 3: Run Tests (when we add them)

```powershell
# Run tests (will add some now)
.\gradlew :shared:test
```

---

## 🧪 Let's Add a Simple Test

I'll create a simple test file so you can see everything working:

### Test File Location
`shared/src/commonTest/kotlin/com/chatty/domain/model/UserTest.kt`

This will test that our User model works correctly.

### Run the Test

```powershell
.\gradlew :shared:test --tests "UserTest"
```

---

## 🎯 What Should We Do Next?

Based on what you want to see working, choose one:

### Option 1: See It Build ✅ (Easiest - 5 minutes)
**Goal**: Verify the project compiles

```powershell
# Just build everything
.\gradlew build
```

**Success means**: All code compiles, SQLDelight generates correctly ✅

---

### Option 2: Create a Simple Desktop App 🖥️ (Moderate - 30 minutes)
**Goal**: See a window with "Hello Chatty!" text

I'll create:
- Desktop app module
- Simple Compose window
- Run configuration

**You can**: Run it and see a real app!

---

### Option 3: Build Minimal Server 🌐 (Advanced - 1 hour)
**Goal**: Running Ktor server you can connect to

I'll create:
- Server module
- Basic endpoints
- In-memory storage (no database needed yet)

**You can**: Test with Postman or browser!

---

### Option 4: Create Full UI Demo 🎨 (Advanced - 2 hours)
**Goal**: Chat UI with mock data

I'll create:
- Login screen
- Chat list screen  
- Chat room screen
- Mock data (no server needed)

**You can**: See the full UI and interact with it!

---

## 💡 My Recommendation for YOU

Since you're asking "what to do", let's start simple and build up:

### Phase 1: Verify Setup (NOW - 5 min)
1. Build the project
2. See it compile
3. Celebrate! 🎉

### Phase 2: See Something Visual (NEXT - 30 min)
1. Create simple desktop app
2. Show a window
3. Feel the progress!

### Phase 3: Make It Real (THEN - 1-2 hours)
1. Add real UI screens
2. OR add server
3. Connect them together

---

## 🎬 Let's Start NOW!

Tell me which you want:

**A)** Just build and test (5 min) - Safe, easy, verify everything works

**B)** Create desktop app with "Hello World" (30 min) - See something visual!

**C)** Create full chat UI with mock data (2 hours) - See it all working!

**D)** Create minimal server (1 hour) - Get backend running!

**E)** I don't know, you choose! - I'll pick the best path for beginners

---

## 🆘 Common Issues & Fixes

### "gradlew: command not found"
```powershell
# Windows PowerShell
.\gradlew build

# Not ./gradlew
```

### "Java version too old"
```powershell
# Install JDK 17 or higher
# Download from: https://adoptium.net/
```

### "Could not resolve dependencies"
```powershell
# Clear Gradle cache
.\gradlew clean
.\gradlew --refresh-dependencies
.\gradlew build
```

### "Build failed"
- Share the error message
- I'll help you fix it!

---

## 📞 What to Do RIGHT NOW

1. Open PowerShell in `f:\kotlin\chatty`
2. Run: `.\gradlew build`
3. Tell me what happens:
   - ✅ Success? → Choose A, B, C, or D above
   - ❌ Error? → Share the error, I'll fix it
   - 🤷 Confused? → Tell me, I'll guide you step by step

**Let's do this! What do you choose?** 🚀
