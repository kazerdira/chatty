package com.chatty.android.di

import com.chatty.android.ui.auth.LoginViewModel
import com.chatty.android.ui.chat.ChatListViewModel
import com.chatty.android.ui.chat.ChatRoomViewModel
import com.chatty.data.local.DatabaseDriverFactory
import com.chatty.data.local.TokenManager
import com.chatty.data.local.TokenManagerImpl
import com.chatty.data.remote.ChatApiClient
import com.chatty.data.repository.AuthRepositoryImpl
import com.chatty.data.repository.ChatRoomRepositoryImpl
import com.chatty.data.repository.MessageRepositoryImpl
import com.chatty.database.ChatDatabase
import com.chatty.domain.repository.AuthRepository
import com.chatty.domain.repository.ChatRoomRepository
import com.chatty.domain.repository.MessageRepository
import com.chatty.domain.usecase.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    
    // Coroutine Scope
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    
    // Database
    single { DatabaseDriverFactory(androidContext()) }
    single { ChatDatabase(get<DatabaseDriverFactory>().createDriver()) }
    
    // Token Manager
    single<TokenManager> { TokenManagerImpl(androidContext()) }
    
    // API Client
    single { 
        ChatApiClient(
            baseUrl = "http://10.0.2.2:8080", // Android emulator localhost
            tokenManager = get()
        )
    }
    
    // Repositories
    single<AuthRepository> { 
        AuthRepositoryImpl(
            apiClient = get(),
            tokenManager = get()
        )
    }
    
    single<MessageRepository> { 
        MessageRepositoryImpl(
            apiClient = get(),
            database = get(),
            tokenManager = get(),
            scope = get()
        )
    }
    
    single<ChatRoomRepository> {
        ChatRoomRepositoryImpl(
            apiClient = get(),
            database = get(),
            tokenManager = get(),
            scope = get()
        )
    }
    
    // Use Cases
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { ObserveMessagesUseCase(get()) }
    factory { GetMessagesUseCase(get()) }
    factory { SendMessageUseCase(get(), get()) }
    factory { ObserveRoomsUseCase(get()) }
    factory { CreateRoomUseCase(get()) }
    
    // ViewModels
    viewModel { LoginViewModel(get(), get()) }
    viewModel { ChatListViewModel(get()) }
    viewModel { (roomId: String) -> 
        ChatRoomViewModel(
            roomId = roomId,
            sendMessageUseCase = get(),
            observeMessagesUseCase = get(),
            getMessagesUseCase = get()
        )
    }
}
