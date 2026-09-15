package com.jackmarcus.anti_clonevoice.webrtc

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface HuggingFaceApi {
    // Exact Meta Wav2Vec2 Model path on Hugging Face
    @POST("models/facebook/wav2vec2-base-960h")
    suspend fun analyzeAudio(
        @Header("Authorization") token: String,
        @Body audioData: RequestBody
    ): List<ScamScoreResponse>
}

@Serializable
data class ScamScoreResponse(
    val label: String,
    val score: Float
)

class CloudInferenceClient(private val apiKey: String) {
    private val TAG = "CloudInference"
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api-inference.huggingface.co/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(HuggingFaceApi::class.java)

    suspend fun getExactScamScore(audioBytes: ByteArray): Float {
        if (apiKey.isEmpty() || apiKey == "hf_placeholder_key") {
            Log.w(TAG, "No valid Hugging Face API key provided")
            return 0f
        }
        
        return try {
            val requestBody = audioBytes.toRequestBody("audio/wav".toMediaType())
            val response = api.analyzeAudio("Bearer $apiKey", requestBody)
            
            // Look for the "fake" or "scam" label score
            val risk = response.find { it.label.lowercase().contains("fake") || it.label.lowercase().contains("scam") }?.score
            val finalScore = (risk ?: 0f) * 100f
            
            Log.i(TAG, "Full Wav2Vec2 API Result: $finalScore%")
            finalScore
        } catch (e: Exception) {
            Log.e(TAG, "Cloud Inference Failed: ${e.message}")
            0f
        }
    }
}
