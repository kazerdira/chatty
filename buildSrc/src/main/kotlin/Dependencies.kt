object Dependencies {
    object Ktor {
        const val serverCore = "io.ktor:ktor-server-core:${Versions.ktor}"
        const val serverNetty = "io.ktor:ktor-server-netty:${Versions.ktor}"
        const val serverWebsockets = "io.ktor:ktor-server-websockets:${Versions.ktor}"
        const val serverContentNegotiation = "io.ktor:ktor-server-content-negotiation:${Versions.ktor}"
        const val serverAuth = "io.ktor:ktor-server-auth:${Versions.ktor}"
        const val serverAuthJwt = "io.ktor:ktor-server-auth-jwt:${Versions.ktor}"
        const val serverSessions = "io.ktor:ktor-server-sessions:${Versions.ktor}"
        const val serverCors = "io.ktor:ktor-server-cors:${Versions.ktor}"
        const val serverCallLogging = "io.ktor:ktor-server-call-logging:${Versions.ktor}"
        const val serializationJson = "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}"
        
        const val clientCore = "io.ktor:ktor-client-core:${Versions.ktor}"
        const val clientWebsockets = "io.ktor:ktor-client-websockets:${Versions.ktor}"
        const val clientContentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor}"
        const val clientLogging = "io.ktor:ktor-client-logging:${Versions.ktor}"
        const val clientAuth = "io.ktor:ktor-client-auth:${Versions.ktor}"
        
        const val clientAndroid = "io.ktor:ktor-client-android:${Versions.ktor}"
        const val clientDarwin = "io.ktor:ktor-client-darwin:${Versions.ktor}"
        const val clientJava = "io.ktor:ktor-client-java:${Versions.ktor}"
    }
    
    object Exposed {
        const val core = "org.jetbrains.exposed:exposed-core:${Versions.exposed}"
        const val dao = "org.jetbrains.exposed:exposed-dao:${Versions.exposed}"
        const val jdbc = "org.jetbrains.exposed:exposed-jdbc:${Versions.exposed}"
        const val javaTime = "org.jetbrains.exposed:exposed-java-time:${Versions.exposed}"
    }
    
    object SqlDelight {
        const val runtime = "app.cash.sqldelight:runtime:${Versions.sqldelight}"
        const val coroutinesExtensions = "app.cash.sqldelight:coroutines-extensions:${Versions.sqldelight}"
        const val androidDriver = "app.cash.sqldelight:android-driver:${Versions.sqldelight}"
        const val nativeDriver = "app.cash.sqldelight:native-driver:${Versions.sqldelight}"
        const val sqliteDriver = "app.cash.sqldelight:sqlite-driver:${Versions.sqldelight}"
    }
    
    object Koin {
        const val core = "io.insert-koin:koin-core:${Versions.koin}"
        const val compose = "io.insert-koin:koin-compose:${Versions.koinCompose}"
        const val android = "io.insert-koin:koin-android:${Versions.koin}"
        const val ktor = "io.insert-koin:koin-ktor:${Versions.koin}"
    }
}
