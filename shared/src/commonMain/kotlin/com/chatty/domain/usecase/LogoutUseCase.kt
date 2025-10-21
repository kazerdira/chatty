package com.chatty.domain.usecase

import com.chatty.data.local.TokenManager

class LogoutUseCase(
    private val tokenManager: TokenManager
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            tokenManager.clearTokens()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
