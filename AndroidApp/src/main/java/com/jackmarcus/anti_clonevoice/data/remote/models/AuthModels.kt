package com.jackmarcus.anti_clonevoice.data.remote.models

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val username: String,
    val password: String,
    val email: String
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String
)

@Serializable
data class ProfileResponse(
    val username: String,
    val email: String,
    val avatarUrl: String
)

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class MessageResponse(
    val message: String
)

@Serializable
data class FirebaseAuthRequest(
    val idToken: String
)
