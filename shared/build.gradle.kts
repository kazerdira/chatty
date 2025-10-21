plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.animation)
                
                // Ktor Client
                implementation(Dependencies.Ktor.clientCore)
                implementation(Dependencies.Ktor.clientWebsockets)
                implementation(Dependencies.Ktor.clientContentNegotiation)
                implementation(Dependencies.Ktor.serializationJson)
                implementation(Dependencies.Ktor.clientLogging)
                implementation(Dependencies.Ktor.clientAuth)
                
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
                
                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}")
                
                // Koin DI
                implementation(Dependencies.Koin.core)
                implementation(Dependencies.Koin.compose)
                
                // SQLDelight
                implementation(Dependencies.SqlDelight.runtime)
                implementation(Dependencies.SqlDelight.coroutinesExtensions)
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.mockk:mockk:${Versions.mockk}")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(Dependencies.Ktor.clientAndroid)
                implementation(Dependencies.SqlDelight.androidDriver)
                implementation(Dependencies.Koin.android)
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(Dependencies.Ktor.clientJava)
                implementation(Dependencies.SqlDelight.sqliteDriver)
            }
        }
    }
}

android {
    namespace = "com.chatty.shared"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 24
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("ChatDatabase") {
            packageName.set("com.chatty.database")
        }
    }
}
