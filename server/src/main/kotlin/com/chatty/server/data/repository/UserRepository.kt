package com.chatty.server.data.repository

import com.chatty.server.UserDto
import com.chatty.server.data.DatabaseFactory.dbQuery
import com.chatty.server.data.tables.Users
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.*

class UserRepository {
    
    suspend fun createUser(username: String, password: String, displayName: String): UserDto = dbQuery {
        val userId = Users.insert {
            it[Users.username] = username
            it[passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt())
            it[Users.displayName] = displayName
            it[status] = "ONLINE"
            it[lastSeen] = Instant.now()
            it[createdAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } get Users.id
        
        getUserById(userId.value.toString())!!
    }
    
    suspend fun getUserById(id: String): UserDto? = dbQuery {
        Users
            .select { Users.id eq UUID.fromString(id) }
            .map { rowToUser(it) }
            .singleOrNull()
    }
    
    suspend fun getUserByUsername(username: String): UserDto? = dbQuery {
        Users
            .select { Users.username eq username }
            .map { rowToUser(it) }
            .singleOrNull()
    }
    
    suspend fun authenticate(username: String, password: String): UserDto? = dbQuery {
        val user = Users
            .select { Users.username eq username }
            .map { it to rowToUser(it) }
            .singleOrNull()
        
        if (user != null && BCrypt.checkpw(password, user.first[Users.passwordHash])) {
            user.second
        } else {
            null
        }
    }
    
    suspend fun searchUsers(query: String): List<UserDto> = dbQuery {
        Users
            .select {
                (Users.username like "%$query%") or
                (Users.displayName like "%$query%")
            }
            .map { rowToUser(it) }
    }
    
    suspend fun updateUserStatus(userId: String, status: String) = dbQuery {
        Users.update({ Users.id eq UUID.fromString(userId) }) {
            it[Users.status] = status
            it[lastSeen] = Instant.now()
            it[updatedAt] = Instant.now()
        }
    }
    
    suspend fun updateUserProfile(
        userId: String,
        displayName: String? = null,
        avatarUrl: String? = null
    ): UserDto? = dbQuery {
        Users.update({ Users.id eq UUID.fromString(userId) }) {
            if (displayName != null) it[Users.displayName] = displayName
            if (avatarUrl != null) it[Users.avatarUrl] = avatarUrl
            it[updatedAt] = Instant.now()
        }
        
        getUserById(userId)
    }
    
    private fun rowToUser(row: ResultRow) = UserDto(
        id = row[Users.id].value.toString(),
        username = row[Users.username],
        displayName = row[Users.displayName],
        avatarUrl = row[Users.avatarUrl] ?: "",
        status = row[Users.status],
        lastSeen = row[Users.lastSeen].toString()
    )
}
