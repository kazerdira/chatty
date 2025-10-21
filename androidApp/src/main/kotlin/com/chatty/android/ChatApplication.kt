package com.chatty.android

import android.app.Application
import com.chatty.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ChatApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin dependency injection
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@ChatApplication)
            modules(appModule)
        }
    }
}
