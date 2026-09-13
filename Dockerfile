# Use Gradle image to build the project
FROM gradle:9.5.0-jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Use installDist to create a distribution folder
RUN ./gradlew :Backend:installDist --no-daemon

# Use a slim JRE image for the runtime
FROM openjdk:11-jre-slim
EXPOSE 8081
RUN mkdir /app
# Copy the distribution from the build stage
COPY --from=build /home/gradle/src/Backend/build/install/Backend /app
# Run the application using the generated startup script
ENTRYPOINT ["/app/bin/Backend"]
