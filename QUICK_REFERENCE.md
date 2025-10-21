# 🎯 Quick Reference Guide

## 📂 Project Structure at a Glance

```
chatty/
├── 📁 buildSrc/              Build configuration & dependencies
├── 📁 shared/                 Multiplatform shared code (MAIN MODULE)
│   ├── 📁 domain/             Business logic (✅ Complete)
│   ├── 📁 data/               Data layer (✅ 80% Complete)
│   └── 📁 presentation/       UI layer (⏳ Not started)
├── 📁 server/                 Ktor backend (⏳ Not started)
├── 📁 androidApp/             Android wrapper (⏳ Not started)
├── 📁 desktopApp/             Desktop wrapper (⏳ Not started)
└── 📄 Documentation files     (✅ Complete)
```

## 🔑 Key Files

### Configuration
- `build.gradle.kts` - Root build file
- `settings.gradle.kts` - Module settings
- `gradle.properties` - Gradle properties
- `shared/build.gradle.kts` - Shared module build

### Domain Models
- `User.kt` - User entity
- `Message.kt` - Message entity with content types
- `ChatRoom.kt` - Chat room entity
- `TypingIndicator.kt` - Typing status

### Repositories (Interfaces)
- `MessageRepository.kt` - Message operations
- `ChatRoomRepository.kt` - Room operations
- `UserRepository.kt` - User operations
- `AuthRepository.kt` - Authentication

### Use Cases
- `SendMessageUseCase.kt` - Send message logic
- `ObserveMessagesUseCase.kt` - Watch messages
- `LoginUseCase.kt` - Login logic
- `RegisterUseCase.kt` - Registration logic

### Network
- `ChatApiClient.kt` - HTTP & WebSocket client
- `WebSocketMessage.kt` - WebSocket message types
- DTOs in `data/remote/dto/`

### Local Storage
- `ChatDatabase.sq` - SQLDelight schema
- `TokenManager.kt` - Token storage interface
- Platform-specific implementations

### Documentation
- `README.md` - Project overview
- `GET_STARTED.md` - Quick start guide
- `ROADMAP.md` - Implementation phases
- `DEVELOPMENT.md` - Dev guidelines
- `STATUS.md` - Current status
- `PROJECT_SUMMARY.md` - Complete summary
- `plan.md` - Original detailed plan

## 🚀 Common Commands

### Build
```powershell
# Build everything
./gradlew build

# Build shared module only
./gradlew :shared:build

# Clean build
./gradlew clean build
```

### Run (After Implementation)
```powershell
# Run server
./gradlew :server:run

# Install Android app
./gradlew :androidApp:installDebug

# Run desktop app
./gradlew :desktopApp:run
```

### Development
```powershell
# Sync dependencies
./gradlew --refresh-dependencies

# See all tasks
./gradlew tasks

# Check for updates
./gradlew dependencyUpdates
```

## 📊 Architecture Layers

```
┌─────────────────────────────────┐
│   Presentation (UI)             │ ⏳ To Do
│   - Screens, ViewModels         │
├─────────────────────────────────┤
│   Domain (Business Logic)       │ ✅ Done
│   - Models, Use Cases           │
├─────────────────────────────────┤
│   Data (Repositories)           │ ✅ 80% Done
│   - Network, Local DB           │
├─────────────────────────────────┤
│   Infrastructure               │ ✅ Done
│   - Platform APIs, DI          │
└─────────────────────────────────┘
```

## 🔄 Data Flow

### Sending a Message
```
User Input (UI)
    ↓
ViewModel calls SendMessageUseCase
    ↓
Use Case validates & creates Message
    ↓
MessageRepository.sendMessage()
    ↓
├─→ Save to local DB (SQLDelight)
└─→ Send via WebSocket (ChatApiClient)
    ↓
Server receives & broadcasts
    ↓
Other clients receive via WebSocket
    ↓
Update local DB & UI
```

### Receiving a Message
```
Server broadcasts message
    ↓
ChatApiClient.incomingMessages Flow
    ↓
MessageRepository listens
    ↓
Save to local DB
    ↓
Flow emits update
    ↓
ViewModel observes
    ↓
UI updates automatically
```

## 🎨 Tech Stack Quick Reference

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 1.9.22 |
| UI Framework | Compose Multiplatform | 1.5.12 |
| Server | Ktor | 2.3.7 |
| HTTP Client | Ktor Client | 2.3.7 |
| Database (Client) | SQLDelight | 2.0.1 |
| Database (Server) | Exposed + PostgreSQL | 0.46.0 |
| DI | Koin | 3.5.3 |
| Serialization | kotlinx.serialization | 1.6.2 |
| Coroutines | kotlinx.coroutines | 1.7.3 |
| DateTime | kotlinx.datetime | 0.5.0 |

## 🗂️ Package Organization

```
com.chatty
├── domain
│   ├── model         # Entities (User, Message, etc.)
│   ├── repository    # Repository interfaces
│   └── usecase       # Business logic
├── data
│   ├── local         # SQLDelight, TokenManager
│   ├── remote        # API client, DTOs
│   └── repository    # Repository implementations
├── presentation
│   ├── viewmodel     # ViewModels
│   ├── ui            # Compose screens & components
│   └── navigation    # Navigation setup
└── di                # Koin modules
```

## 🔍 Finding Things

### "Where is...?"

- **User model?** → `shared/src/commonMain/kotlin/com/chatty/domain/model/User.kt`
- **Send message logic?** → `shared/src/commonMain/kotlin/com/chatty/domain/usecase/SendMessageUseCase.kt`
- **Network client?** → `shared/src/commonMain/kotlin/com/chatty/data/remote/ChatApiClient.kt`
- **Database schema?** → `shared/src/commonMain/sqldelight/com/chatty/database/ChatDatabase.sq`
- **WebSocket messages?** → `shared/src/commonMain/kotlin/com/chatty/data/remote/dto/WebSocketMessage.kt`
- **Android storage?** → `shared/src/androidMain/kotlin/com/chatty/data/local/`
- **Desktop storage?** → `shared/src/desktopMain/kotlin/com/chatty/data/local/`

## 💡 Design Patterns Used

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Clean Architecture** | Entire project | Separation of concerns |
| **Repository** | Data layer | Abstract data sources |
| **Use Case** | Domain layer | Business logic |
| **DTO** | Data layer | Data transfer objects |
| **Factory** | Platform code | Create platform objects |
| **Observer** | Throughout | Reactive data streams |
| **Singleton** | API client | Single instance |
| **Value Object** | IDs | Type-safe identifiers |

## 🎯 Design Decisions

### Why Clean Architecture?
- Testable business logic
- Independent of frameworks
- Independent of UI
- Independent of database
- Easy to maintain and extend

### Why Multiplatform?
- Share business logic across platforms
- Write once, run everywhere
- Consistent behavior
- Reduced maintenance

### Why WebSocket?
- Real-time communication
- Bi-directional
- Efficient for chat
- Push notifications alternative

### Why SQLDelight?
- Type-safe SQL
- Compile-time verification
- Platform-agnostic
- Great tooling

### Why Local-First?
- Instant UI updates
- Offline support
- Better UX
- Reduced server load

## 🔐 Security Features

✅ **Implemented:**
- Encrypted token storage (Android)
- Secure preferences (Desktop)
- JWT authentication ready
- SQL injection prevention (SQLDelight)

⏳ **To Implement:**
- HTTPS enforcement
- Token refresh logic
- Rate limiting (server)
- Input validation
- CORS configuration (server)

## 🐛 Troubleshooting Quick Fixes

### Build Issues
```powershell
./gradlew clean
./gradlew --stop
./gradlew build --refresh-dependencies
```

### Database Issues
- Android: Clear app data
- Desktop: Delete `~/.chatty/`
- Check schema version

### Network Issues
- Verify server is running
- Check baseUrl in ChatApiClient.kt
- Enable logging in Ktor client

### IDE Issues
- File → Invalidate Caches / Restart
- Reimport Gradle project
- Update IntelliJ/Android Studio

## 📝 Code Snippets

### Create a New Use Case
```kotlin
class MyUseCase(
    private val repository: MyRepository
) {
    suspend operator fun invoke(params: Params): Result<Output> {
        return repository.doSomething(params)
    }
    
    data class Params(/* params */)
}
```

### Create a New Repository
```kotlin
// Interface
interface MyRepository {
    suspend fun doSomething(): Result<Data>
}

// Implementation
class MyRepositoryImpl(
    private val apiClient: ChatApiClient,
    private val database: ChatDatabase
) : MyRepository {
    override suspend fun doSomething(): Result<Data> {
        // Implementation
    }
}
```

### Observe Data with Flow
```kotlin
fun observeData(): Flow<List<Item>> {
    return database.queries
        .selectAll()
        .asFlow()
        .mapToList(Dispatchers.Default)
}
```

## 🎓 Learning Path

1. **Start Here:** Read README.md
2. **Understand Plan:** Read plan.md
3. **See Status:** Read STATUS.md
4. **Start Building:** Read GET_STARTED.md
5. **Follow Steps:** Read ROADMAP.md
6. **Best Practices:** Read DEVELOPMENT.md
7. **Explore Code:** Start with domain/model/
8. **Build Server:** Follow server implementation guide
9. **Build UI:** Follow UI implementation guide
10. **Deploy:** Follow deployment guide (in plan.md)

## 📞 Need Help?

### Documentation Order
1. Quick overview? → **STATUS.md**
2. Want to start? → **GET_STARTED.md**
3. Need steps? → **ROADMAP.md**
4. Building code? → **DEVELOPMENT.md**
5. Deep dive? → **plan.md**

### Common Questions

**Q: What should I build first?**
A: See GET_STARTED.md - Recommendation: Build the server

**Q: How do I test?**
A: Build server first, then use existing client code

**Q: Where do I add new features?**
A: Start in domain layer, then data, then presentation

**Q: How do I add a new screen?**
A: Create ViewModel, then Screen, then add to navigation

**Q: How do I add a new entity?**
A: Model → Repository → Use Case → ViewModel → UI

## 🎯 Success Checklist

### You're Ready When You Can:
- [ ] Explain the architecture layers
- [ ] Find any file in the project
- [ ] Understand the data flow
- [ ] Know which pattern to use
- [ ] Add a new feature independently
- [ ] Debug common issues
- [ ] Write tests for new code

### You've Succeeded When:
- [ ] Server is running
- [ ] Client connects to server
- [ ] Messages send/receive in real-time
- [ ] UI displays correctly
- [ ] Offline mode works
- [ ] Tests pass
- [ ] App is deployed

## 🏁 Next Action

👉 **Go to [GET_STARTED.md](GET_STARTED.md) and choose your path!**

---

**Quick Links:**
- [📖 Full Documentation](README.md)
- [🚀 Get Started](GET_STARTED.md)
- [🗺️ Roadmap](ROADMAP.md)
- [📊 Status](STATUS.md)
- [📋 Summary](PROJECT_SUMMARY.md)
- [🔧 Dev Guide](DEVELOPMENT.md)
- [📝 Original Plan](plan.md)
