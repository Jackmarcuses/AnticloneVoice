package com.jackmarcus.anti_clonevoice.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import kotlinx.serialization.Serializable

@Serializable
data class TranscriptRequest(
    val content: String,
    val language: String
)

interface TranscriptService {
    @POST("api/v1/transcripts")
    suspend fun saveTranscript(@Body request: TranscriptRequest): Response<Unit>
}
