package com.jackmarcus.backend

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import com.jackmarcus.backend.models.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.jackmarcus.backend.database.UserDatabase

class ApplicationTest {
    @Test
    fun testSignup() = testApplication {
        application {
            module(isTest = true)
        }
        UserDatabase.clear()
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val email = "test@example.com"
        client.post("/api/v1/auth/send-otp?email=$email")
        val otp = UserDatabase.getOtp(email) ?: ""

        val response = client.post("/api/v1/signup?otp=$otp") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest("testuser", "password", email))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("token"))
    }

    @Test
    fun testLogin() = testApplication {
        application {
            module(isTest = true)
        }
        UserDatabase.clear()
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        // Setup user
        val email = "login@example.com"
        client.post("/api/v1/auth/send-otp?email=$email")
        val otp = UserDatabase.getOtp(email) ?: ""

        val signupResponse = client.post("/api/v1/signup?otp=$otp") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest("loginuser", "password", email))
        }
        assertEquals(HttpStatusCode.Created, signupResponse.status)

        val response = client.post("/api/v1/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("loginuser", "password"))
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        val authResponse = Json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertNotNull(authResponse.token)
    }
}
