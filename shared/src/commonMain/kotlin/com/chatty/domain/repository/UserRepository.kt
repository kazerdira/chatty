package com.chatty.domain.repository

import com.chatty.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun getCurrentUserId(): String?
    suspend fun getUser(userId: User.UserId): User?
    suspend fun getUsers(userIds: List<User.UserId>): List<User>
    suspend fun searchUsers(query: String): Result<List<User>>
    suspend fun updateUserStatus(status: User.UserStatus): Result<Unit>
    fun observeUserStatus(userId: User.UserId): Flow<User.UserStatus>
    suspend fun updateProfile(displayName: String?, avatarUrl: String?): Result<User>
}
