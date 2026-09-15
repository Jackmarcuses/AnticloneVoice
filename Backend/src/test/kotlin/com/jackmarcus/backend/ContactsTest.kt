package com.jackmarcus.backend

import com.jackmarcus.backend.database.UserDatabase
import com.jackmarcus.backend.models.*
import com.jackmarcus.backend.plugins.generateToken
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ContactsTest {

    @Test
    fun testContactManagement() = testApplication {
        application {
            module(isTest = true)
        }
        UserDatabase.clear()
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val user1 = User("user1", "alice", "hash", "alice@example.com")
        val user2 = User("user2", "bob", "hash", "bob@example.com")
        UserDatabase.addUser(user1)
        UserDatabase.addUser(user2)

        val token = generateToken(user1.id)
        
        // Add contact
        val addResponse = client.post("/api/v1/contacts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(AddContactRequest(user2.id))
        }
        assertEquals(HttpStatusCode.OK, addResponse.status)
        
        // Get contacts
        val contactsResponse = client.get("/api/v1/contacts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, contactsResponse.status)
        assertTrue(contactsResponse.bodyAsText().contains("bob"))

        // Delete contact
        val deleteResponse = client.delete("/api/v1/contacts/${user2.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify deleted
        val contactsAfterDelete = client.get("/api/v1/contacts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertFalse(contactsAfterDelete.bodyAsText().contains("bob"))
    }

    @Test
    fun testPresenceBroadcast() = testApplication {
        // ... (existing test)
    }

    @Test
    fun testSignalingRouting() = testApplication {
        application {
            module(isTest = true)
        }
        UserDatabase.clear()

        val user1Id = "user1"
        val user2Id = "user2"

        val client2 = createClient { install(WebSockets) }
        client2.webSocket("/api/v1/call/signal/$user2Id") {
            val client1 = createClient { install(WebSockets) }
            client1.webSocket("/api/v1/call/signal/$user1Id") {
                val message = SignalingMessage("offer", user1Id, user2Id, "test-sdp")
                send(Frame.Text(Json.encodeToString(message)))
            }

            val frame = incoming.receive() as Frame.Text
            val received = Json.decodeFromString<SignalingMessage>(frame.readText())
            assertEquals("offer", received.type)
            assertEquals(user1Id, received.senderId)
            assertEquals("test-sdp", received.data)
        }
    }
}
