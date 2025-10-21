package com.chatty.domain.usecase

import com.chatty.domain.repository.AuthRepository

class RegisterUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        displayName: String
    ): Result<Unit> {
        return authRepository.register(username, password, displayName)
            .map { }
    }
}
