# Stage 1: Build the project
FROM eclipse-temurin:17-jdk AS build
WORKDIR /home/gradle/src
COPY . .

# Ensure the gradlew script is executable
RUN chmod +x ./gradlew

# Build the Backend distribution
RUN ./gradlew :Backend:installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
EXPOSE 8081
WORKDIR /app

# Copy the build artifacts from the build stage
COPY --from=build /home/gradle/src/Backend/build/install/Backend /app

# Run the application
ENTRYPOINT ["/app/bin/Backend"]
