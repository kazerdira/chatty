package com.chatty.server.data

import com.chatty.server.data.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.UUID

object DatabaseFactory {
    
    fun init(
        jdbcUrl: String = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/chatty",
        driverClass: String = "org.postgresql.Driver",
        user: String = System.getenv("DB_USER") ?: "chatty",
        password: String = System.getenv("DB_PASSWORD") ?: "chatty123"
    ) {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.driverClassName = driverClass
            this.username = user
            this.password = password
            this.maximumPoolSize = 10
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        
        val dataSource = HikariDataSource(config)
        val database = Database.connect(dataSource)
        
        transaction(database) {
            addLogger(StdOutSqlLogger)
            
            // Create tables
            SchemaUtils.create(
                Users,
                ChatRooms,
                RoomParticipants,
                Messages,
                MessageStatus,
                RefreshTokens,
                TypingIndicators
            )
            
            // Insert test data if empty
            if (Users.selectAll().count() == 0L) {
                insertTestData()
            }
        }
    }
    
    private fun insertTestData() {
        // Create test users
        val aliceUuid = UUID.randomUUID()
        Users.insert {
            it[id] = aliceUuid
            it[username] = "alice"
            it[displayName] = "Alice Johnson"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[Users.status] = "ONLINE"
            it[avatarUrl] = "https://i.pravatar.cc/150?img=1"
        }
        
        val bobUuid = UUID.randomUUID()
        Users.insert {
            it[id] = bobUuid
            it[username] = "bob"
            it[displayName] = "Bob Smith"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[Users.status] = "ONLINE"
            it[avatarUrl] = "https://i.pravatar.cc/150?img=2"
        }
        
        val charlieUuid = UUID.randomUUID()
        Users.insert {
            it[id] = charlieUuid
            it[username] = "charlie"
            it[displayName] = "Charlie Brown"
            it[passwordHash] = BCrypt.hashpw("password123", BCrypt.gensalt())
            it[Users.status] = "OFFLINE"
            it[avatarUrl] = "https://i.pravatar.cc/150?img=3"
        }
        
        // Create a test chat room
        val roomUuid = UUID.randomUUID()
        ChatRooms.insert {
            it[id] = roomUuid
            it[name] = "General Chat"
            it[type] = "GROUP"
            it[avatarUrl] = null
            it[createdBy] = aliceUuid
        }
        
        // Add participants to room
        RoomParticipants.insert {
            it[RoomParticipants.roomId] = roomUuid
            it[userId] = aliceUuid
            it[role] = "ADMIN"
        }
        
        RoomParticipants.insert {
            it[RoomParticipants.roomId] = roomUuid
            it[userId] = bobUuid
            it[role] = "MEMBER"
        }
        
        RoomParticipants.insert {
            it[RoomParticipants.roomId] = roomUuid
            it[userId] = charlieUuid
            it[role] = "MEMBER"
        }
        
        // Add some test messages
        val message1Uuid = UUID.randomUUID()
        Messages.insert {
            it[id] = message1Uuid
            it[Messages.roomId] = roomUuid
            it[senderId] = aliceUuid
            it[contentType] = "TEXT"
            it[contentText] = "Hey everyone! Welcome to the chat!"
        }
        
        val message2Uuid = UUID.randomUUID()
        Messages.insert {
            it[id] = message2Uuid
            it[Messages.roomId] = roomUuid
            it[senderId] = bobUuid
            it[contentType] = "TEXT"
            it[contentText] = "Thanks Alice! Happy to be here 🎉"
        }
        
        println("✅ Test data inserted successfully")
        println("   Users: alice, bob, charlie (password: password123)")
        println("   Room: General Chat")
        println("   Messages: 2 test messages")
    }
    
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
