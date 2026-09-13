package com.jackmarcus.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.firebase.auth.FirebaseAuth
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Application.configureSecurity() {
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "anti-clone-voice"
    val jwtDomain = System.getenv("JWT_DOMAIN") ?: "https://api.anti-clonevoice.com/"
    val jwtRealm = "ktor-auth-realm"
    val jwtSecret = System.getenv("JWT_SECRET") ?: "secret-key" // In production, use environment variable

    authentication {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtDomain)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { defaultScheme, realm ->
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

fun generateToken(userId: String): String {
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "anti-clone-voice"
    val jwtDomain = System.getenv("JWT_DOMAIN") ?: "https://api.anti-clonevoice.com/"
    val jwtSecret = System.getenv("JWT_SECRET") ?: "secret-key"
    
    return JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtDomain)
        .withClaim("userId", userId)
        .sign(Algorithm.HMAC256(jwtSecret))
}

suspend fun verifyFirebaseToken(idToken: String): String? = withContext(Dispatchers.IO) {
    try {
        // Only try to verify if Firebase has been initialized
        if (com.google.firebase.FirebaseApp.getApps().isNotEmpty()) {
            val decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken)
            decodedToken.uid
        } else {
            println("WARNING: Firebase not initialized. Using 'test_uid' for debugging.")
            "test_uid_${idToken.take(5)}" 
        }
    } catch (e: Exception) {
        println("ERROR: Firebase token verification failed: ${e.message}")
        null
    }
}
