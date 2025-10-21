# Development Guide

## Project Setup

### Initial Setup
1. Clone the repository
2. Open in IntelliJ IDEA or Android Studio
3. Sync Gradle files
4. Wait for dependencies to download

### Environment Configuration

#### Server Configuration
Create `server/src/main/resources/application.conf`:
```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ com.chatty.server.ApplicationKt.module ]
    }
}

jwt {
    secret = "your-secret-key-change-in-production"
    issuer = "https://chatty.app"
    audience = "chatty-users"
    realm = "Chatty App"
}

database {
    url = "jdbc:postgresql://localhost:5432/chatty"
    driver = "org.postgresql.Driver"
    user = "chatty_user"
    password = "change_me"
}
```

#### Client Configuration
Update `shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt`:
```kotlin
private val baseUrl: String = "http://localhost:8080"  // Development
// private val baseUrl: String = "https://api.chatty.app"  // Production
```

## Development Workflow

### Running the Server
```bash
cd server
./gradlew run
```

### Running Android App
```bash
./gradlew :androidApp:installDebug
```

### Running Desktop App
```bash
./gradlew :desktopApp:run
```

## Code Organization

### Naming Conventions

#### Files
- **Models**: `User.kt`, `Message.kt`
- **ViewModels**: `ChatRoomViewModel.kt`, `LoginViewModel.kt`
- **Screens**: `ChatListScreen.kt`, `LoginScreen.kt`
- **Repositories**: `MessageRepository.kt`, `MessageRepositoryImpl.kt`
- **Use Cases**: `SendMessageUseCase.kt`

#### Classes
- **DTOs**: `MessageDto`, `UserDto`
- **Entities**: `User`, `Message`
- **ViewModels**: `ChatRoomViewModel`, `LoginViewModel`

### Package Structure

```
com.chatty
├── domain
│   ├── model/          # Core business models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic
├── data
│   ├── local/          # Local storage (SQLDelight, SharedPreferences)
│   ├── remote/         # Network layer (Ktor client, DTOs)
│   └── repository/     # Repository implementations
├── presentation
│   ├── ui
│   │   ├── screens/    # Compose screens
│   │   ├── components/ # Reusable UI components
│   │   └── theme/      # Theme configuration
│   └── viewmodel/      # ViewModels
└── di/                 # Dependency injection modules
```

## Coding Standards

### Kotlin Style Guide
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation
- Max line length: 120 characters

### Architecture Guidelines

#### Clean Architecture Layers
1. **Domain Layer**: Pure Kotlin, no platform dependencies
2. **Data Layer**: Platform-agnostic with expect/actual for platform-specific code
3. **Presentation Layer**: Compose UI, ViewModels

#### Dependency Rule
- Outer layers depend on inner layers
- Domain layer has no dependencies
- Data layer depends on domain
- Presentation depends on domain and data

### Repository Pattern
```kotlin
// Domain layer - Interface
interface MessageRepository {
    suspend fun sendMessage(message: Message): Result<Message>
}

// Data layer - Implementation
class MessageRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val database: ChatDatabase
) : MessageRepository {
    override suspend fun sendMessage(message: Message): Result<Message> {
        // Implementation
    }
}
```

### Use Case Pattern
```kotlin
class SendMessageUseCase(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(params: Params): Result<Message> {
        return messageRepository.sendMessage(params.message)
    }
    
    data class Params(val message: Message)
}
```

### ViewModel Pattern
```kotlin
class ChatRoomViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            sendMessageUseCase(params).fold(
                onSuccess = { /* Handle success */ },
                onFailure = { /* Handle error */ }
            )
        }
    }
    
    sealed class UiState {
        object Loading : UiState()
        data class Success(val messages: List<Message>) : UiState()
        data class Error(val message: String) : UiState()
    }
}
```

## Testing

### Unit Tests
```kotlin
class SendMessageUseCaseTest : StringSpec({
    "should send message successfully" {
        // Arrange
        val mockRepository = mockk<MessageRepository>()
        val useCase = SendMessageUseCase(mockRepository)
        
        // Act & Assert
        // Test implementation
    }
})
```

### Integration Tests
```kotlin
class MessageRepositoryTest : StringSpec({
    "should sync messages from server" {
        // Test with actual API client and local database
    }
})
```

### UI Tests
```kotlin
@Test
fun testChatListScreen() = runComposeUiTest {
    setContent {
        ChatListScreen(/* ... */)
    }
    
    onNodeWithText("My Room").assertExists()
    onNodeWithText("My Room").performClick()
}
```

## Debugging

### Logging
```kotlin
// Use platform-specific logging
expect fun logDebug(tag: String, message: String)
expect fun logError(tag: String, message: String, throwable: Throwable?)

// Usage
logDebug("ChatRoom", "Sending message: $text")
```

### Network Debugging
Enable Ktor logging:
```kotlin
install(Logging) {
    level = LogLevel.ALL  // Change to BODY, INFO, or NONE as needed
}
```

### Database Debugging
Enable SQL logging:
```kotlin
database.chatDatabaseQueries.selectMessages(roomId, limit)
    .also { println("SQL: ${it.sql}") }
    .executeAsList()
```

## Common Tasks

### Adding a New Feature

1. **Define Domain Model** (if needed)
   ```kotlin
   // domain/model/NewFeature.kt
   data class NewFeature(...)
   ```

2. **Create Repository Interface**
   ```kotlin
   // domain/repository/NewFeatureRepository.kt
   interface NewFeatureRepository {
       suspend fun doSomething(): Result<NewFeature>
   }
   ```

3. **Implement Use Case**
   ```kotlin
   // domain/usecase/DoSomethingUseCase.kt
   class DoSomethingUseCase(private val repository: NewFeatureRepository) { ... }
   ```

4. **Implement Repository**
   ```kotlin
   // data/repository/NewFeatureRepositoryImpl.kt
   class NewFeatureRepositoryImpl(...) : NewFeatureRepository { ... }
   ```

5. **Create ViewModel**
   ```kotlin
   // presentation/viewmodel/NewFeatureViewModel.kt
   class NewFeatureViewModel(private val useCase: DoSomethingUseCase) { ... }
   ```

6. **Create UI**
   ```kotlin
   // presentation/ui/screens/NewFeatureScreen.kt
   @Composable
   fun NewFeatureScreen(viewModel: NewFeatureViewModel) { ... }
   ```

### Adding a Database Table

1. Update `ChatDatabase.sq`:
   ```sql
   CREATE TABLE NewTable (
       id TEXT PRIMARY KEY NOT NULL,
       name TEXT NOT NULL
   );
   
   selectAll:
   SELECT * FROM NewTable;
   
   insert:
   INSERT INTO NewTable VALUES (?, ?);
   ```

2. Build project to generate queries

3. Use in repository:
   ```kotlin
   database.chatDatabaseQueries.insert(id, name)
   ```

## Performance Tips

1. **Use Flow for reactive data**
   ```kotlin
   fun observeMessages(): Flow<List<Message>> {
       return database.selectMessages()
           .asFlow()
           .mapToList()
   }
   ```

2. **Implement pagination**
   ```kotlin
   suspend fun getMessages(before: MessageId?, limit: Int = 50)
   ```

3. **Cache network responses**
   ```kotlin
   // Save to local database after network call
   apiClient.getMessages().onSuccess { messages ->
       database.insertMessages(messages)
   }
   ```

4. **Use coroutines properly**
   ```kotlin
   // Don't block main thread
   viewModelScope.launch(Dispatchers.IO) {
       // Heavy operation
   }
   ```

## Security Best Practices

1. **Never store passwords in plain text**
2. **Use HTTPS in production**
3. **Validate all user input**
4. **Implement rate limiting**
5. **Use secure token storage** (already implemented)
6. **Sanitize SQL queries** (SQLDelight handles this)
7. **Implement proper authentication**
8. **Use CORS properly on server**

## Troubleshooting

### Gradle Issues
```bash
./gradlew clean
./gradlew --stop
./gradlew build --refresh-dependencies
```

### Database Issues
- Delete app data (Android)
- Delete `~/.chatty/` directory (Desktop)
- Check database schema version

### Network Issues
- Check server is running
- Verify baseUrl configuration
- Check firewall/proxy settings
- Enable network logging

## Resources

- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor Documentation](https://ktor.io/docs/)
- [SQLDelight](https://cashapp.github.io/sqldelight/)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
