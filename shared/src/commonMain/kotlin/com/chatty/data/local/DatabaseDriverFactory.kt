package com.chatty.data.local

import com.chatty.database.ChatDatabase
import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): ChatDatabase {
    val driver = driverFactory.createDriver()
    return ChatDatabase(driver)
}
