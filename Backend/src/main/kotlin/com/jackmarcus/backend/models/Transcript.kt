package com.jackmarcus.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class TranscriptRequest(
    val content: String,
    val language: String
)
