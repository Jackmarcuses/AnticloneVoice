package com.jackmarcus.anti_clonevoice.data.remote.models

import kotlinx.serialization.Serializable

@Serializable
data class SignalingMessage(
    val type: String, // "offer", "answer", "candidate", "call_request", "call_response"
    val senderId: String,
    val receiverId: String,
    val data: String? = null
)
