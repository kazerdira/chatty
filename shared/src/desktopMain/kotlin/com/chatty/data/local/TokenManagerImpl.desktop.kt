package com.chatty.data.local

import java.util.prefs.Preferences

actual class TokenManagerImpl : TokenManager {
    private val prefs = Preferences.userRoot().node("chatty_tokens")
    
    override suspend fun saveAccessToken(token: String) {
        prefs.put(KEY_ACCESS_TOKEN, token)
        prefs.flush()
    }
    
    override suspend fun saveRefreshToken(token: String) {
        prefs.put(KEY_REFRESH_TOKEN, token)
        prefs.flush()
    }
    
    override suspend fun getAccessToken(): String? {
        return prefs.get(KEY_ACCESS_TOKEN, null)
    }
    
    override suspend fun getRefreshToken(): String? {
        return prefs.get(KEY_REFRESH_TOKEN, null)
    }
    
    override suspend fun clearTokens() {
        prefs.remove(KEY_ACCESS_TOKEN)
        prefs.remove(KEY_REFRESH_TOKEN)
        prefs.remove(KEY_USER_ID)
        prefs.remove(KEY_USERNAME)
        prefs.remove(KEY_DISPLAY_NAME)
        prefs.flush()
    }
    
    override suspend fun saveUserId(userId: String) {
        prefs.put(KEY_USER_ID, userId)
        prefs.flush()
    }
    
    override suspend fun getUserId(): String? {
        return prefs.get(KEY_USER_ID, null)
    }
    
    override suspend fun saveUserInfo(userId: String, username: String, displayName: String) {
        prefs.put(KEY_USER_ID, userId)
        prefs.put(KEY_USERNAME, username)
        prefs.put(KEY_DISPLAY_NAME, displayName)
        prefs.flush()
    }
    
    override suspend fun getUsername(): String? {
        return prefs.get(KEY_USERNAME, null)
    }
    
    override suspend fun getDisplayName(): String? {
        return prefs.get(KEY_DISPLAY_NAME, null)
    }
    
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
