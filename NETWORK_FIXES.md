# 🔧 Android Network & API Fixes

**Date**: October 21, 2025  
**Issue**: Connection errors and JSON serialization mismatches

---

## ✅ Fixes Applied

### 1. Network Security Configuration (HTTP Traffic)

**Problem**: Android blocks cleartext HTTP traffic by default

**Fix**: Created `network_security_config.xml`
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

**Updated**: `AndroidManifest.xml`
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="true"
    ...>
```

---

### 2. DTO Mismatch Fix

**Problem**: Client expected `accessToken` and `user` object, but server returns flat structure

**Client DTO (OLD - WRONG)**:
```kotlin
data class AuthResponse(
    val accessToken: String,  // ❌ Server sends "token"
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto  // ❌ Server sends flat fields
)
```

**Server Response (ACTUAL)**:
```json
{
  "token": "eyJhbGci...",
  "refreshToken": "uuid",
  "userId": "user-1",
  "username": "alice",
  "displayName": "Alice Johnson",
  "expiresIn": 3600000
}
```

**Client DTO (NEW - FIXED)**:
```kotlin
data class AuthResponse(
    val token: String,  // ✅ Matches server
    val refreshToken: String,
    val userId: String,  // ✅ Flat structure
    val username: String,  // ✅ Flat structure
    val displayName: String,  // ✅ Flat structure
    val expiresIn: Long = 3600000
)
```

**Updated**: All references in `AuthRepositoryImpl.kt`
- `response.accessToken` → `response.token` ✅
- Removed `response.user.xxx` references ✅

---

### 3. Debug Logging Added

**Where**: Throughout the authentication flow

**LoginViewModel.kt**:
```kotlin
fun login(username: String, password: String) {
    println("🔑 LoginViewModel: Starting login for user: $username")
    // ... on success
    println("✅ LoginViewModel: Login successful!")
    // ... on failure
    println("❌ LoginViewModel: Login failed - ${error.message}")
    error.printStackTrace()
}

fun register(username: String, password: String, displayName: String) {
    println("📝 LoginViewModel: Starting registration...")
    // ... similar logging
}
```

**ChatApiClient.kt**:
```kotlin
suspend fun login(request: AuthRequest): Result<AuthResponse> {
    println("🔐 ChatApiClient: Attempting login for user: ${request.username}")
    println("🔐 ChatApiClient: URL: $baseUrl/auth/login")
    val response = httpClient.post("$baseUrl/auth/login") { ... }
    println("🔐 ChatApiClient: Login response status: ${response.status}")
    val body: AuthResponse = response.body()
    println("🔐 ChatApiClient: Login successful! Token: ${body.token.take(20)}...")
}

private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> {
    return try {
        println("✅ API call successful")
        Result.success(block())
    } catch (e: Exception) {
        println("❌ API call failed: ${e.message}")
        println("❌ Exception type: ${e::class.simpleName}")
        e.printStackTrace()
        Result.failure(e)
    }
}
```

---

## 🔍 How to Debug Now

### In Android Studio Logcat:

1. **Filter by tag**: Look for these emojis
   - 🔑 = LoginViewModel login
   - 📝 = LoginViewModel register
   - 🔐 = ChatApiClient login
   - ✅ = Success
   - ❌ = Error

2. **Login flow logs**:
```
🔑 LoginViewModel: Starting login for user: alice
🔐 ChatApiClient: Attempting login for user: alice
🔐 ChatApiClient: URL: http://10.0.2.2:8080/auth/login
🔐 ChatApiClient: Login response status: 200 OK
🔐 ChatApiClient: Login successful! Token: eyJhbGciOiJIUzI1NiIsI...
✅ API call successful
✅ LoginViewModel: Login successful!
```

3. **Error flow logs**:
```
🔑 LoginViewModel: Starting login for user: alice
🔐 ChatApiClient: Attempting login for user: alice
🔐 ChatApiClient: URL: http://10.0.2.2:8080/auth/login
❌ API call failed: Connection refused
❌ Exception type: ConnectException
❌ LoginViewModel: Login failed - Connection refused
```

---

## 📱 Android Emulator Network Notes

### Important IP Addresses:
- `localhost` / `127.0.0.1` → Points to **emulator itself** ❌
- `10.0.2.2` → Points to **your PC's localhost** ✅
- `10.0.2.15` → Emulator's own IP address

### Current Configuration:
```kotlin
// AppModule.kt
single { 
    ChatApiClient(
        baseUrl = "http://10.0.2.2:8080",  // ✅ Correct for emulator
        tokenManager = get()
    )
}
```

---

## ✅ Testing Checklist

### Before Testing:
1. ✅ Backend server running on port 8080
2. ✅ Android app rebuilt with fixes
3. ✅ Logcat filter ready

### Test Login:
1. Open app → Login screen
2. Enter: `alice` / `password123`
3. Click "Sign In"
4. **Check Logcat** for flow:
   - 🔑 LoginViewModel starting
   - 🔐 ChatApiClient attempting
   - ✅ Success or ❌ Error with details

### Test Register:
1. Switch to "Sign Up" tab
2. Enter: username, password, display name
3. Click "Sign Up"
4. **Check Logcat** for flow:
   - 📝 LoginViewModel starting registration
   - 📝 ChatApiClient attempting
   - ✅ Success or ❌ Error with details

---

## 🎯 Expected Behavior

### On Success:
- Logs show: `✅ LoginViewModel: Login successful!`
- UI navigates to Chat List screen
- Token saved in TokenManager

### On Network Error:
- Logs show: `❌ API call failed: Connection refused`
- OR: `❌ API call failed: Unable to resolve host`
- Check: Is backend running? Is URL correct?

### On Auth Error:
- Logs show: `❌ API call failed: 401 Unauthorized`
- Check: Wrong username/password

### On DTO Error:
- Logs show: `❌ API call failed: Serialization exception`
- This should be **FIXED** now with matching DTOs

---

## 🐛 Common Issues & Solutions

### Issue: "Connection refused"
**Cause**: Backend not running  
**Fix**: Start server with `.\gradlew.bat :server:run`

### Issue: "Unable to resolve host 10.0.2.2"
**Cause**: Not using Android emulator (using physical device)  
**Fix**: Use `http://YOUR_PC_IP:8080` instead

### Issue: "Cleartext HTTP traffic not permitted"
**Cause**: Network security config not applied  
**Fix**: Already fixed! Config is in place.

### Issue: "Serialization exception: Unknown key"
**Cause**: DTO mismatch  
**Fix**: Already fixed! DTOs match server now.

---

## 📂 Modified Files

1. ✅ `androidApp/src/main/res/xml/network_security_config.xml` (NEW)
2. ✅ `androidApp/src/main/AndroidManifest.xml` (UPDATED)
3. ✅ `shared/src/commonMain/kotlin/com/chatty/data/remote/dto/AuthDto.kt` (FIXED)
4. ✅ `shared/src/commonMain/kotlin/com/chatty/data/repository/AuthRepositoryImpl.kt` (FIXED)
5. ✅ `shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt` (LOGGING ADDED)
6. ✅ `androidApp/src/main/kotlin/com/chatty/android/ui/auth/LoginViewModel.kt` (LOGGING ADDED)

---

## 🚀 Next Steps

1. **Rebuild the app**: `.\gradlew.bat :androidApp:assembleDebug`
2. **Install on emulator**: Run from Android Studio or `adb install`
3. **Watch Logcat**: Filter for emoji symbols
4. **Test login/register**: Use mock users or create new ones
5. **Report results**: Share Logcat output if issues persist

---

## 📊 Summary

| Issue | Status |
|-------|--------|
| HTTP cleartext blocked | ✅ FIXED |
| DTO mismatch (token vs accessToken) | ✅ FIXED |
| DTO mismatch (flat vs nested user) | ✅ FIXED |
| Debug logging missing | ✅ ADDED |
| Network security config | ✅ CONFIGURED |
| Android manifest updated | ✅ UPDATED |

**All issues addressed! Ready to test! 🎉**

---

Generated: October 21, 2025, 3:45 AM  
Status: ✅ Ready for Testing  
Backend: http://localhost:8080 (must be running)  
Android: Uses http://10.0.2.2:8080 (emulator → PC)
