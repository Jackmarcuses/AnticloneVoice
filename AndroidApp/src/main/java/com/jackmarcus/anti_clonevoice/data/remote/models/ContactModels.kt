package com.jackmarcus.anti_clonevoice.data.remote.models

import kotlinx.serialization.Serializable

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
