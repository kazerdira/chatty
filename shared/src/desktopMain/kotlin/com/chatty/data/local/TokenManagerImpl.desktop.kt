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
        prefs.flush()
    }
    
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
