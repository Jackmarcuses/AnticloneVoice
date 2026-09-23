package com.jackmarcus.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val passwordHash: String,
    val email: String,
    val avatarUrl: String = "",
    val contacts: List<String> = emptyList(),
    val voiceEmbedding: String? = null,
    val baselineSpeechRate: Float = 0.0f,
    val pitchVariance: Float = 0.0f
)

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
data class ContactResponse(
    val userId: String,
    val username: String,
    val isOnline: Boolean,
    val voiceEmbedding: String? = null,
    val baselineSpeechRate: Float = 0.0f,
    val pitchVariance: Float = 0.0f
)

@Serializable
data class AddContactRequest(
    val contactId: String
)

@Serializable
data class PresenceUpdate(
    val userId: String,
    val status: String
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
data class UpdateVoiceProfileRequest(
    val voiceEmbedding: String,
    val baselineSpeechRate: Float,
    val pitchVariance: Float
)

@Serializable
data class FirebaseAuthRequest(
    val idToken: String
)

@Serializable
data class VaultItem(
    val id: String,
    val name: String,
    val embedding: String,
    val wps: Float,
    val pitch: Float
)

@Serializable
data class VaultSaveRequest(
    val ownerId: String,
    val contactId: String,
    val name: String,
    val embedding: String,
    val wps: Float,
    val pitch: Float
)
