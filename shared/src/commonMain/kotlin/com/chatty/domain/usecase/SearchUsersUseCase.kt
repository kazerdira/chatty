package com.chatty.domain.usecase

import com.chatty.domain.model.User
import com.chatty.domain.repository.UserRepository

class SearchUsersUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(query: String): Result<List<User>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        return userRepository.searchUsers(query)
    }
}
