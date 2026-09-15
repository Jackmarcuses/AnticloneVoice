package com.jackmarcus.anti_clonevoice.data.repository

import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.NetworkClient
import com.jackmarcus.anti_clonevoice.data.remote.TranscriptRequest
import com.jackmarcus.anti_clonevoice.data.remote.TranscriptService
import android.util.Log

class TranscriptRepository(
    private val transcriptService: TranscriptService = NetworkClient.transcriptService
) {
    private val TAG = "TranscriptRepository"

    suspend fun saveParagraph(content: String, language: String): Result<Unit> {
        return try {
            val response = transcriptService.saveTranscript(TranscriptRequest(content, language))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to save transcript: ${response.code()}")
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving transcript: ${e.message}")
            Result.failure(e)
        }
    }
}
