FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY gradlew gradlew.bat ./

# Copy source code
COPY shared shared
COPY server server

# Build the application
RUN ./gradlew :server:build --no-daemon

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built artifact
COPY --from=builder /app/server/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
