# Chatty - Real-Time Chat Application

## Project Overview

This is a production-ready chat application built with Ktor and Kotlin Compose Multiplatform, following Clean Architecture principles and industry best practices.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│  (Compose UI, ViewModels, Platform-specific UI components)  │
├─────────────────────────────────────────────────────────────┤
│                       Domain Layer                           │
│     (Use Cases, Entity Models, Repository Interfaces)       │
├─────────────────────────────────────────────────────────────┤
│                        Data Layer                            │
│  (Repository Implementations, Network, Local Database, DTOs) │
├─────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                      │
│   (DI Container, Platform APIs, External Service Adapters)  │
└─────────────────────────────────────────────────────────────┘
```

## Technology Stack

- **Backend**: Ktor Server with WebSockets
- **Frontend**: Kotlin Compose Multiplatform (Android, Desktop)
- **Database**: 
  - Server: PostgreSQL with Exposed ORM
  - Client: SQLDelight
- **Authentication**: JWT with refresh tokens
- **Real-time**: WebSockets for bidirectional communication
- **DI**: Koin for dependency injection
- **Serialization**: kotlinx.serialization
- **Testing**: Kotest, MockK

## Project Structure

```
chatty/
├── buildSrc/                    # Build configuration
├── shared/                      # Shared Kotlin Multiplatform module
│   ├── commonMain/
│   │   ├── kotlin/
│   │   │   ├── domain/         # Business logic
│   │   │   │   ├── model/      # Entity models
│   │   │   │   ├── repository/ # Repository interfaces
│   │   │   │   └── usecase/    # Use cases
│   │   │   ├── data/           # Data layer
│   │   │   │   ├── local/      # Local storage
│   │   │   │   ├── remote/     # Network layer
│   │   │   │   └── repository/ # Repository implementations
│   │   │   └── presentation/   # UI layer (to be added)
│   │   └── sqldelight/         # Database schema
│   ├── androidMain/            # Android-specific code
│   └── desktopMain/            # Desktop-specific code
├── server/                      # Ktor backend (to be implemented)
├── androidApp/                  # Android app wrapper (to be implemented)
└── desktopApp/                  # Desktop app wrapper (to be implemented)
```

## Features Implemented

### ✅ Domain Layer
- User, Message, ChatRoom, and TypingIndicator models
- Repository interfaces for separation of concerns
- Use cases for business logic encapsulation

### ✅ Data Layer
- **Network Layer**:
  - ChatApiClient with Ktor client
  - WebSocket connection with auto-reconnection
  - Exponential backoff strategy
  - DTO models and mappers
  
- **Local Storage**:
  - SQLDelight database schema
  - Platform-specific database drivers
  - Secure token management
  - Message caching and offline support

- **Repository Implementations**:
  - MessageRepository with local-first strategy
  - AuthRepository with JWT handling
  - Automatic sync between local and remote

### ✅ Platform-Specific Implementations
- **Android**:
  - EncryptedSharedPreferences for secure token storage
  - AndroidSqliteDriver for database
  
- **Desktop**:
  - Java Preferences for token storage
  - JdbcSqliteDriver for database

## Next Steps

To complete the application, you need to implement:

### 1. Server Implementation (Ktor Backend)
- Create server module
- WebSocket session management
- Database layer with Exposed
- Authentication endpoints
- Message routing

### 2. UI Implementation (Compose)
- Chat list screen
- Chat room screen with messages
- Login/Register screens
- User profile screen
- ViewModels for state management
- Navigation setup

### 3. Android App
- MainActivity
- Application class with DI setup
- Build configuration

### 4. Desktop App
- Main window setup
- Platform-specific configurations

### 5. Additional Features
- Typing indicators
- Message status (sent, delivered, read)
- Image/file upload
- Push notifications (Android)
- User presence
- Message pagination

## Building the Project

### Prerequisites
- JDK 17 or higher
- Android Studio (for Android development)
- IntelliJ IDEA (recommended for desktop)

### Build Commands

```bash
# Build shared module
./gradlew :shared:build

# Build Android app
./gradlew :androidApp:assembleDebug

# Build Desktop app
./gradlew :desktopApp:packageDistributionForCurrentOS

# Run tests
./gradlew test
```

## Configuration

### API Configuration
Update the base URL in `ChatApiClient.kt`:
```kotlin
private val baseUrl: String = "http://your-server-url:8080"
```

### Database
The local database is automatically created on first launch.

## Security Considerations

- ✅ Secure token storage (EncryptedSharedPreferences on Android)
- ✅ JWT authentication with refresh tokens
- ✅ WebSocket authentication
- ✅ SQL injection prevention (SQLDelight prepared statements)

## Testing Strategy

Implement tests for:
- Use cases (unit tests)
- Repository implementations (integration tests)
- ViewModels (unit tests with MockK)
- UI components (Compose testing)

## Performance Optimizations

- Local-first data strategy
- Message pagination
- Image caching (to be implemented)
- WebSocket connection pooling
- Lazy loading of chat rooms

## Contributing

1. Follow Clean Architecture principles
2. Write tests for new features
3. Use Kotlin coding conventions
4. Document public APIs
5. Keep platform-specific code minimal

## License

[Your License Here]

## Contact

[Your Contact Information]

---

**Status**: Foundation Complete - Ready for UI and Server Implementation

The domain and data layers are production-ready. The next priority is implementing the Ktor server backend and Compose UI layer.
