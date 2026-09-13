package com.jackmarcus.backend.plugins

import com.jackmarcus.backend.database.UserDatabase
import com.jackmarcus.backend.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import java.util.*
import kotlin.random.Random

fun Application.configureRouting() {
    routing {
        post("/api/v1/auth/send-otp") {
            val email = call.parameters["email"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Email required")
            val code = (100000..999999).random().toString()
            
            UserDatabase.saveOtp(email, code)
            
            // TODO: In production, call your Email Service here (SendGrid, Mailgun, etc.)
            println("SIMULATED EMAIL to $email: Your OTP is $code")
            
            call.respond(HttpStatusCode.OK, MessageResponse("OTP sent successfully to $email"))
        }

        post("/api/v1/signup") {
            val request = call.receive<SignupRequest>()
            val otpCode = call.parameters["otp"]
            
            if (otpCode == null || UserDatabase.getOtp(request.email) != otpCode) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid or expired OTP"))
                return@post
            }
            
            if (UserDatabase.getUser(request.username) != null) {
                call.respond(HttpStatusCode.Conflict, MessageResponse("User already exists"))
                return@post
            }
            
            val hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt())
            val newUser = User(
                id = UUID.randomUUID().toString(),
                username = request.username,
                passwordHash = hashedPassword,
                email = request.email
            )
            UserDatabase.addUser(newUser)
            UserDatabase.removeOtp(request.email)
            
            val token = generateToken(newUser.id)
            call.respond(HttpStatusCode.Created, AuthResponse(token, newUser.id))
        }

        post("/api/v1/login") {
            val request = call.receive<LoginRequest>()
            val user = UserDatabase.getUser(request.username)
            
            if (user == null || !BCrypt.checkpw(request.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid username or password"))
                return@post
            }
            
            val token = generateToken(user.id)
            call.respond(AuthResponse(token, user.id))
        }

        post("/api/v1/auth/firebase") {
            val request = call.receive<FirebaseAuthRequest>()
            val firebaseUid = verifyFirebaseToken(request.idToken)
            
            if (firebaseUid == null) {
                call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid Firebase token"))
                return@post
            }
            
            var user = UserDatabase.getUserById(firebaseUid)
            
            if (user == null) {
                user = User(
                    id = firebaseUid,
                    username = "user_${firebaseUid.take(8)}",
                    passwordHash = "",
                    email = ""
                )
                UserDatabase.addUser(user)
            }
            
            val token = generateToken(user.id)
            call.respond(AuthResponse(token, user.id))
        }

        authenticate("auth-jwt") {
            get("/api/v1/profile") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid token"))
                    return@get
                }
                
                val user = UserDatabase.getUserById(userId)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, MessageResponse("User not found"))
                    return@get
                }
                
                call.respond(ProfileResponse(user.username, user.email, user.avatarUrl))
            }

            patch("/api/v1/profile") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateProfileRequest>()
                
                if (UserDatabase.updateUser(userId, request.username, request.avatarUrl)) {
                    call.respond(HttpStatusCode.OK, MessageResponse("Profile updated"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("Could not update profile"))
                }
            }

            get("/api/v1/users/search") {
                val query = call.request.queryParameters["query"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Query required")
                val users = UserDatabase.searchUsers(query).map {
                    ContactResponse(it.id, it.username, UserDatabase.isOnline(it.id))
                }
                call.respond(users)
            }

            get("/api/v1/contacts") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val contacts = UserDatabase.getContacts(userId).map {
                    ContactResponse(it.id, it.username, UserDatabase.isOnline(it.id))
                }
                call.respond(contacts)
            }

            post("/api/v1/contacts") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<AddContactRequest>()
                
                if (UserDatabase.addContact(userId, request.contactId)) {
                    call.respond(HttpStatusCode.OK, MessageResponse("Contact added"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("Could not add contact"))
                }
            }

            delete("/api/v1/contacts/{contactId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val contactId = call.parameters["contactId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                
                if (UserDatabase.removeContact(userId, contactId)) {
                    call.respond(HttpStatusCode.OK, MessageResponse("Contact removed"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("Could not remove contact"))
                }
            }
        }
    }
}
