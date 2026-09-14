package com.jackmarcus.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Int? = null,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long
)

@Serializable
data class ChatMessagePayload(
    val type: String = "chat_message",
    val message: Message
)
