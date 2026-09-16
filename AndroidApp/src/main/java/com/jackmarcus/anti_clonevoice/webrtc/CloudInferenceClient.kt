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
    // Exact Meta Wav2Vec2 Model path on Hugging Face (ASR version)
    @POST("models/facebook/wav2vec2-base-960h")
    suspend fun transcribeAudio(
        @Header("Authorization") token: String,
        @Body audioData: RequestBody
    ): TranscriptionResponse
}

@Serializable
data class TranscriptionResponse(
    val text: String? = null
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

    suspend fun getTranscription(audioBytes: ByteArray): String? {
        if (apiKey.isEmpty() || apiKey == "hf_placeholder_key" || apiKey == "YOUR_KEY_HERE") {
            Log.w(TAG, "No valid Hugging Face API key provided")
            return null
        }
        
        return try {
            val requestBody = audioBytes.toRequestBody("audio/wav".toMediaType())
            val response = api.transcribeAudio("Bearer $apiKey", requestBody)
            
            Log.i(TAG, "Full Wav2Vec2 ASR Result: ${response.text}")
            response.text
        } catch (e: Exception) {
            Log.e(TAG, "Cloud Transcription Failed: ${e.message}")
            null
        }
    }
}
