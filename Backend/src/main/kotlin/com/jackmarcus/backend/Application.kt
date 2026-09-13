package com.jackmarcus.backend

import com.jackmarcus.backend.database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.jackmarcus.backend.plugins.*
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("Application")
    logger.info("Starting backend server...")
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureFirebaseAdmin()
    configureSerialization()
    configureSecurity()
    configureSockets()
    configureRouting()
}
