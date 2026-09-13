package com.jackmarcus.backend

import com.jackmarcus.backend.database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.jackmarcus.backend.plugins.*
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("Application")
    
    // Render provides a PORT environment variable. If it's missing, use 8081.
    val port = System.getenv("PORT")?.toInt() ?: 8081
    
    logger.info("Starting backend server on port $port...")
    
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")
    logger.info("Initializing Application modules...")
    DatabaseFactory.init()
    configureFirebaseAdmin()
    configureSerialization()
    configureSecurity()
    configureSockets()
    configureRouting()
    logger.info("Application modules initialized successfully.")
}
