# Stage 1: Build the project
FROM gradle:8.4.0-jdk11 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

# Ensure the gradlew script is executable
RUN chmod +x ./gradlew

# Build the Backend distribution without configuration cache to avoid serialization errors on Render
RUN ./gradlew :Backend:installDist --no-daemon --no-configuration-cache

# Stage 2: Runtime
FROM eclipse-temurin:11-jre
EXPOSE 8081
WORKDIR /app

# Copy the build artifacts from the build stage
COPY --from=build /home/gradle/src/Backend/build/install/Backend /app

# Run the application
ENTRYPOINT ["/app/bin/Backend"]
