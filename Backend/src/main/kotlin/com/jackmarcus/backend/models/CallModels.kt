package com.jackmarcus.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class SignalingMessage(
    val type: String, // "offer", "answer", "candidate", "call_request", "call_response"
    val senderId: String,
    val receiverId: String,
    val data: String? = null // SDP or ICE candidate JSON string
)

@Serializable
data class CallRequest(
    val callerId: String,
    val callerName: String,
    val receiverId: String
)

@Serializable
data class CallResponse(
    val receiverId: String,
    val callerId: String,
    val accepted: Boolean
)
