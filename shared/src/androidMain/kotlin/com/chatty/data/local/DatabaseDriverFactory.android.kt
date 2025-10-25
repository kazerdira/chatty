package com.chatty.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.chatty.database.ChatDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = ChatDatabase.Schema,
            context = context,
            name = "chatty.db",
            callback = object : AndroidSqliteDriver.Callback(ChatDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Add MessageOutbox table if it doesn't exist (migration for fix6)
                    db.execSQL("""
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
                    """.trimIndent())
                    
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS idx_outbox_status_created 
                        ON MessageOutbox(status, createdAt)
                    """.trimIndent())
                    
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS idx_outbox_room 
                        ON MessageOutbox(roomId)
                    """.trimIndent())
                }
            }
        )
    }
}
