package com.chatty.data.repository

import com.chatty.data.local.TokenManager
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.remote.dto.toEntity
import com.chatty.domain.model.User
import com.chatty.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class UserRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val tokenManager: TokenManager
) : UserRepository {
    
    override suspend fun getCurrentUser(): User? {
        // TODO: Implement when we have a GET /users/me endpoint
        return null
    }
    
    override suspend fun getUser(userId: User.UserId): User? {
        // TODO: Implement when we have a GET /users/{id} endpoint
        return null
    }
    
    override suspend fun getUsers(userIds: List<User.UserId>): List<User> {
        // TODO: Implement when we have a batch GET endpoint
        return emptyList()
    }
    
    override suspend fun searchUsers(query: String): Result<List<User>> {
        return apiClient.searchUsers(query).map { dtoList ->
            dtoList.map { dto ->
                dto.toEntity()
            }
        }
    }
    
    override suspend fun updateUserStatus(status: User.UserStatus): Result<Unit> {
        // TODO: Implement when we have a PATCH /users/me/status endpoint
        return Result.success(Unit)
    }
    
    override fun observeUserStatus(userId: User.UserId): Flow<User.UserStatus> {
        // TODO: Implement with WebSocket
        return flowOf(User.UserStatus.OFFLINE)
    }
    
    override suspend fun updateProfile(displayName: String?, avatarUrl: String?): Result<User> {
        // TODO: Implement when we have a PATCH /users/me endpoint
        return Result.failure(Exception("Not implemented"))
    }
}
