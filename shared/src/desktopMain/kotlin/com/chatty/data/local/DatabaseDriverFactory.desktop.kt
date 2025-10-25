package com.chatty.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chatty.database.ChatDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val databasePath = File(System.getProperty("user.home"), ".chatty/chatty.db")
        databasePath.parentFile.mkdirs()
        
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePath}")
        
        // Create schema if new database, or migrate if existing
        try {
            ChatDatabase.Schema.create(driver)
        } catch (e: Exception) {
            // Database already exists, add migration for MessageOutbox table
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS MessageOutbox (
                    id TEXT PRIMARY KEY NOT NULL,
                    roomId TEXT NOT NULL,
                    senderId TEXT NOT NULL,
                    contentType TEXT NOT NULL,
                    contentData TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    retryCount INTEGER NOT NULL DEFAULT 0,
                    lastRetryAt INTEGER,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            
            driver.execute(null, """
                CREATE INDEX IF NOT EXISTS idx_outbox_status_created 
                ON MessageOutbox(status, createdAt)
            """.trimIndent(), 0)
            
            driver.execute(null, """
                CREATE INDEX IF NOT EXISTS idx_outbox_room 
                ON MessageOutbox(roomId)
            """.trimIndent(), 0)
        }
        
        return driver
    }
}
