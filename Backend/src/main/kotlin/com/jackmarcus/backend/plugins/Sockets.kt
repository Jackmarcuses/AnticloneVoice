package com.jackmarcus.backend.plugins

import com.jackmarcus.backend.database.UserDatabase
import com.jackmarcus.backend.models.PresenceUpdate
import com.jackmarcus.backend.models.SignalingMessage
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val userSessions = ConcurrentHashMap<String, WebSocketServerSession>()

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }

    routing {
        webSocket("/presence/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket
            
            userSessions[userId] = this
            UserDatabase.setUserOnline(userId)
            
            // Broadcast "online" to contacts
            broadcastPresence(userId, "online")
            
            try {
                for (frame in incoming) {
                    // Handle messages if needed, but presence is mostly connection based here
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                userSessions.remove(userId)
                UserDatabase.setUserOffline(userId)
                // Broadcast "offline" to contacts
                broadcastPresence(userId, "offline")
            }
        }

        webSocket("/api/v1/call/signal/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket
            userSessions[userId] = this

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = Json.decodeFromString<SignalingMessage>(text)
                        
                        // Route to receiver
                        val receiverSession = userSessions[message.receiverId]
                        if (receiverSession != null) {
                            receiverSession.send(Frame.Text(text))
                        } else {
                            // Optionally send back "user_unavailable"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                userSessions.remove(userId)
            }
        }
    }
}

suspend fun broadcastPresence(userId: String, status: String) {
    val contacts = UserDatabase.getContacts(userId)
    val message = PresenceUpdate(userId, status)
    val jsonMessage = Json.encodeToString(message)
    
    // Broadcast to all of the user's contacts who are currently online
    contacts.forEach { contact ->
        userSessions[contact.id]?.send(Frame.Text(jsonMessage))
    }
}
