package com.jackmarcus.anti_clonevoice.webrtc

import com.jackmarcus.anti_clonevoice.data.Config
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.OkHttpClient
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface VoiceIntegrityApi {
    @POST("analyze_voice")
    suspend fun analyzeVoice(
        @Body audioData: RequestBody,
        @Query("userId") userId: String,
        @Query("ownerId") ownerId: String,
        @Query("name") name: String,
        @Query("wps") wps: Float,
        @Query("pitch") pitch: Float,
        @Query("enroll") enroll: Boolean = false,
        @Query("checkAgainst") checkAgainst: String? = null
    ): VoiceAnalysisResponse

    @GET("get_vault")
    suspend fun getVault(
        @Query("ownerId") ownerId: String
    ): VaultResponse

    @POST("enroll_voice")
    suspend fun enrollVoice(
        @Body request: EnrollRequest
    ): EnrollResponse
}

@Serializable
data class EnrollRequest(
    val ownerId: String,
    val contactId: String,
    val name: String,
    val samples: List<String>
)

@Serializable
data class EnrollResponse(
    val success: Boolean = false,
    val samplesProcessed: Int = 0,
    val message: String = ""
)

@Serializable
data class VaultItem(
    val id: String,
    val name: String,
    val embedding: String = "",
    val wps: Float = 0f,
    val pitch: Float = 0f
)

@Serializable
data class VaultResponse(
    val vault: List<VaultItem>
)

@Serializable
data class VoiceAnalysisResponse(
    val dhwaniRisk: Float = 0f,
    val identityScore: Float = 0f,
    val behavioralScore: Float = 0f,
    val isSpeech: Boolean = false
)

class CloudInferenceClient(private val apiKey: String) {
    private val TAG = "CloudInference"
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://${Config.localIp}:5000/") // Local IP of your PC
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(VoiceIntegrityApi::class.java)

    suspend fun getVoiceAnalysis(
        audioBytes: ByteArray, 
        userId: String, 
        ownerId: String,
        userName: String = "Unknown Caller",
        wps: Float = 0f, 
        pitch: Float = 0f, 
        enroll: Boolean = false, 
        checkAgainst: String? = null
    ): VoiceAnalysisResponse? {
        return try {
            val requestBody = audioBytes.toRequestBody("audio/wav".toMediaType())
            val response = api.analyzeVoice(requestBody, userId, ownerId, userName, wps, pitch, enroll, checkAgainst)
            
            Log.i(TAG, "Analysis Result -> Dhwani: ${response.dhwaniRisk}% | Identity: ${response.identityScore} | Behav: ${response.behavioralScore} | Enrolled: $enroll | CheckAgainst: $checkAgainst")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Voice Analysis Failed: ${e.message}")
            null
        }
    }

    suspend fun getVaultList(ownerId: String): List<VaultItem> {
        return try {
            val response = api.getVault(ownerId)
            response.vault
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch vault: ${e.message}")
            emptyList()
        }
    }

    suspend fun enrollVoiceSamples(
        ownerId: String,
        contactId: String,
        name: String,
        samplesBase64: List<String>
    ): EnrollResponse? {
        return try {
            val req = EnrollRequest(ownerId, contactId, name, samplesBase64)
            api.enrollVoice(req)
        } catch (e: Exception) {
            Log.e(TAG, "Enrollment Failed: ${e.message}")
            null
        }
    }
}
