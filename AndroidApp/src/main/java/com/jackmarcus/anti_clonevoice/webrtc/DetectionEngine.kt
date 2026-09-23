package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import com.jackmarcus.anti_clonevoice.data.repository.TranscriptRepository
import kotlinx.coroutines.*
import java.nio.ByteBuffer

data class IntegrityResult(
    val riskScore: Float,
    val message: String,
    val threatLevel: String,
    val recommendChallenge: Boolean = false,
    val acousticAnomaly: Float = 0f,
    val identityMismatch: Float = 0f,
    val behavioralAnomaly: Float = 0f
)

class DetectionEngine(
    context: Context,
    private val observer: WebRtcClient.WebRtcObserver,
    private val transcriptRepository: TranscriptRepository
) : AutoCloseable {
    private val TAG = "DetectionEngine"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val voiceGuard = VoiceGuardDetector()
    private val deepfakeModel = VoiceDeepfakeModelEngine(context)
    private val biometricEngine = VoiceBiometricEngine(context)
    private val prosodyAnalyzer = ProsodyAnalyzer()
    private val wav2vecDetector = Wav2VecDetector(context)
    private val cloudClient = CloudInferenceClient("") 
    
    private var dhwaniRiskScore = 0f
    private var serverIdentityScore = 1.0f
    private var serverBehavioralScore = 1.0f
    private var serverIsSpeech = false
    private var remoteUserId = "unknown"
    private var remoteUserName = "Unknown Caller"
    private var checkAgainstUserId: String? = null
    private var myUserId = "unknown"
    private var requestCounter = 0L
    private var currentUiState: IntegrityResult = IntegrityResult(0f, "Analyzing...", "GENUINE")
    private var consecutiveMismatchCount = 0
    private val callStartTime = System.currentTimeMillis()
    private var storedEmbedding: FloatArray? = null

    private var smoothedAcousticRisk = 0f
    private var smoothedIdentityMismatch = 0f
    private var smoothedBehavioralMismatch = 0f
    
    @Volatile
    private var isClosed = false
    
    private var shouldEnroll = false

    fun resetEngineState() {
        serverIdentityScore = 1.0f
        serverBehavioralScore = 1.0f
        dhwaniRiskScore = 0f
        smoothedAcousticRisk = 0f
        smoothedIdentityMismatch = 0f
        smoothedBehavioralMismatch = 0f
        consecutiveMismatchCount = 0
        checkAgainstUserId = null
        currentUiState = IntegrityResult(0f, "Verifying voice identity...", "GENUINE")
        Log.i(TAG, "Detection engine state reset to clean defaults")
    }

    fun setStoredProfile(embedding: FloatArray?, rate: Float = 0f, variance: Float = 0f) {
        this.storedEmbedding = embedding
        Log.i(TAG, "Stored voice profile updated (embedding size: ${embedding?.size ?: 0})")
    }

    fun setRemoteUserId(userId: String, userName: String = "Unknown Caller") {
        this.remoteUserId = userId
        this.remoteUserName = userName
    }

    fun setMyUserId(userId: String) {
        this.myUserId = userId
    }

    fun setCheckAgainstUserId(userId: String?, targetEmbedding: FloatArray? = null) {
        this.checkAgainstUserId = userId
        if (targetEmbedding != null) {
            this.storedEmbedding = targetEmbedding
        }
        serverIdentityScore = 1.0f 
        serverBehavioralScore = 1.0f
        smoothedIdentityMismatch = 0f
        consecutiveMismatchCount = 0
        Log.i(TAG, "Cross-check target set to '$userId' (embedding provided: ${targetEmbedding != null})")
    }

    fun enrollVoice() {
        this.shouldEnroll = true
        Log.i(TAG, "Enrollment requested for $remoteUserId")
    }

    fun processDigitalAudioForTranscription(audioData: ByteBuffer, sampleRate: Int, numChannels: Int) {}
    fun updateTranscript(text: String) {}

    fun processAudioWindow(pcmData: ShortArray, sampleRate: Int): IntegrityResult {
        if (isClosed) return IntegrityResult(0f, "Engine Closed", "UNKNOWN")
        
        val floatData = FloatArray(pcmData.size) { i -> pcmData[i] / 32768.0f }
        
        // 1. Acoustic Integrity (Local DSP + Neural)
        val guardResult = voiceGuard.analyzeAudioChunk(pcmData)
        val modelResult = deepfakeModel.detectVoiceClone(floatData)
        val wav2vecScore = wav2vecDetector.analyzeLatentFeatures(floatData)
        
        // 2. Behavioral Prosody (Local)
        val prosodyMetrics = prosodyAnalyzer.analyzeProsody(floatData, sampleRate)

        val isSpeech = guardResult.isSpeechActive

        // 3. Compute Real-time Local Anomaly Scores
        val rawAcoustic = if (isSpeech) {
            maxOf(guardResult.riskScore, modelResult.riskScore, wav2vecScore, dhwaniRiskScore).coerceIn(0f, 100f)
        } else 0f

        val rawIdentityMismatch = if (isSpeech && storedEmbedding != null) {
            val liveVec = biometricEngine.extractEmbedding(floatData)
            val sim = biometricEngine.calculateIdentityScore(liveVec, storedEmbedding!!)
            val threshold = if (checkAgainstUserId != null) 0.65f else 0.55f
            val localMismatch = if (sim < threshold) ((threshold - sim) / threshold * 100f).coerceIn(0f, 100f) else 0f
            val serverMismatch = ((1.0f - serverIdentityScore) * 100f).coerceIn(0f, 100f)
            maxOf(localMismatch, serverMismatch)
        } else if (isSpeech) {
            ((1.0f - serverIdentityScore) * 100f).coerceIn(0f, 100f)
        } else 0f

        val rawBehavioralMismatch = if (isSpeech) {
            val localUrgency = (prosodyMetrics.urgencyScore - 25f).coerceIn(0f, 100f)
            val serverBeh = ((1.0f - serverBehavioralScore) * 100f).coerceIn(0f, 100f)
            maxOf(localUrgency, serverBeh)
        } else 0f

        // 4. Temporal Exponential Moving Average (EMA) Smoothing
        if (isSpeech) {
            smoothedAcousticRisk = (smoothedAcousticRisk * 0.75f) + (rawAcoustic * 0.25f)
            smoothedIdentityMismatch = (smoothedIdentityMismatch * 0.75f) + (rawIdentityMismatch * 0.25f)
            smoothedBehavioralMismatch = (smoothedBehavioralMismatch * 0.75f) + (rawBehavioralMismatch * 0.25f)
        } else {
            smoothedAcousticRisk *= 0.7f
            smoothedIdentityMismatch *= 0.7f
            smoothedBehavioralMismatch *= 0.7f
            if (smoothedAcousticRisk < 0.1f) smoothedAcousticRisk = 0f
            if (smoothedIdentityMismatch < 0.1f) smoothedIdentityMismatch = 0f
            if (smoothedBehavioralMismatch < 0.1f) smoothedBehavioralMismatch = 0f
        }

        val finalRisk = ((smoothedAcousticRisk * 0.6f) + (smoothedIdentityMismatch * 0.25f) + (smoothedBehavioralMismatch * 0.15f)).coerceIn(0f, 100f)
        val (msg, level, recommend) = generateAlertMessage(finalRisk, smoothedAcousticRisk, smoothedIdentityMismatch, smoothedBehavioralMismatch, isSpeech, checkAgainstUserId)
        
        currentUiState = IntegrityResult(
            riskScore = finalRisk,
            message = msg,
            threatLevel = level,
            recommendChallenge = recommend,
            acousticAnomaly = smoothedAcousticRisk,
            identityMismatch = smoothedIdentityMismatch,
            behavioralAnomaly = smoothedBehavioralMismatch
        )

        // Push real-time smoothed metrics to UI immediately
        observer.onDetectionResult(
            riskScore = finalRisk,
            message = msg,
            level = level,
            recommendChallenge = recommend,
            identityMatch = smoothedIdentityMismatch,
            acousticAuth = smoothedAcousticRisk,
            behavioralMatch = smoothedBehavioralMismatch
        )

        // 5. Trigger Async Cloud Analysis
        val audioBytes = pcmToWav(pcmData, sampleRate)
        val currentEnrollFlag = shouldEnroll
        val targetCheckId = checkAgainstUserId
        val requestId = ++requestCounter
        shouldEnroll = false

        scope.launch(Dispatchers.IO) {
            if (isClosed || !isActive) return@launch
            val response = cloudClient.getVoiceAnalysis(
                audioBytes = audioBytes, 
                userId = remoteUserId, 
                ownerId = myUserId,
                userName = remoteUserName,
                wps = prosodyMetrics.speechRate, 
                pitch = prosodyMetrics.pitchVariance, 
                enroll = currentEnrollFlag,
                checkAgainst = targetCheckId
            )
            
            if (requestId >= requestCounter && response != null) {
                dhwaniRiskScore = response.dhwaniRisk.coerceIn(0f, 100f)
                val safeIdScore = if (response.identityScore > 1.0f) response.identityScore / 100.0f else response.identityScore
                val safeBehScore = if (response.behavioralScore > 1.0f) response.behavioralScore / 100.0f else response.behavioralScore

                serverIdentityScore = ((serverIdentityScore * 0.3f) + (safeIdScore.coerceIn(0f, 1f) * 0.7f)).coerceIn(0f, 1f)
                serverBehavioralScore = ((serverBehavioralScore * 0.3f) + (safeBehScore.coerceIn(0f, 1f) * 0.7f)).coerceIn(0f, 1f)
                serverIsSpeech = response.isSpeech
            }
        }

        return currentUiState
    }

    private fun generateAlertMessage(
        clampedRisk: Float, 
        acousticScore: Float, 
        identityMismatch: Float, 
        behavioralMismatch: Float,
        isSpeech: Boolean,
        targetCheckId: String?
    ): Triple<String, String, Boolean> {
        val timeSinceStart = System.currentTimeMillis() - callStartTime
        if (identityMismatch >= 25f && isSpeech) {
            consecutiveMismatchCount++
        } else {
            consecutiveMismatchCount = 0
        }

        val recommendChallenge = consecutiveMismatchCount >= 3 && timeSinceStart > 3000
        
        val message = when {
            !isSpeech && clampedRisk < 30 -> "Monitoring silence..."
            clampedRisk >= 90 || acousticScore >= 95 -> "CRITICAL: Synthetic TTS / Voice Clone Detected"
            identityMismatch >= 50 -> {
                if (targetCheckId != null) "CRITICAL: Voice DOES NOT match $targetCheckId"
                else "CRITICAL: Voice Fingerprint Mismatch (Identity)"
            }
            dhwaniRiskScore >= 75 -> "CRITICAL: Synthetic Phase Anomaly Detected"
            clampedRisk >= 75 -> "CRITICAL: Potential Impersonation Attempt"
            acousticScore >= 50 -> "WARNING: Synthetic Voice Signature"
            identityMismatch >= 25 -> "WARNING: Identity Mismatch"
            behavioralMismatch >= 40 -> "SUSPICIOUS: Unnatural Voice Behavior"
            else -> {
                if (targetCheckId != null) "VERIFIED: Voice matches $targetCheckId"
                else "Voice Verified: Secure"
            }
        }
        
        val level = when {
            clampedRisk >= 60 || acousticScore >= 80 || identityMismatch >= 50 -> "CRITICAL"
            clampedRisk >= 30 || identityMismatch >= 25 || behavioralMismatch >= 30 -> "SUSPICIOUS"
            else -> "GENUINE"
        }
        
        return Triple(message, level, recommendChallenge)
    }

    override fun close() {
        isClosed = true
        scope.cancel()
        deepfakeModel.close()
        wav2vecDetector.close()
        biometricEngine.close()
    }

    private fun pcmToWav(pcmData: ShortArray, sampleRate: Int): ByteArray {
        val headerSize = 44
        val byteData = ByteArray(pcmData.size * 2)
        for (i in pcmData.indices) {
            val s = pcmData[i].toInt()
            byteData[i * 2] = (s and 0x00FF).toByte()
            byteData[i * 2 + 1] = (s shr 8).toByte()
        }

        val totalDataLen = byteData.size + headerSize - 8
        val byteRate = sampleRate * 2 

        val wavHeader = ByteArray(headerSize)
        wavHeader[0] = 'R'.code.toByte(); wavHeader[1] = 'I'.code.toByte(); wavHeader[2] = 'F'.code.toByte(); wavHeader[3] = 'F'.code.toByte()
        wavHeader[4] = (totalDataLen and 0xff).toByte(); wavHeader[5] = (totalDataLen shr 8 and 0xff).toByte()
        wavHeader[6] = (totalDataLen shr 16 and 0xff).toByte(); wavHeader[7] = (totalDataLen shr 24 and 0xff).toByte()
        wavHeader[8] = 'W'.code.toByte(); wavHeader[9] = 'A'.code.toByte(); wavHeader[10] = 'V'.code.toByte(); wavHeader[11] = 'E'.code.toByte()
        wavHeader[12] = 'f'.code.toByte(); wavHeader[13] = 'm'.code.toByte(); wavHeader[14] = 't'.code.toByte(); wavHeader[15] = ' '.code.toByte()
        wavHeader[16] = 16; wavHeader[17] = 0; wavHeader[18] = 0; wavHeader[19] = 0 
        wavHeader[20] = 1; wavHeader[21] = 0 
        wavHeader[22] = 1; wavHeader[23] = 0 
        wavHeader[24] = (sampleRate and 0xff).toByte(); wavHeader[25] = (sampleRate shr 8 and 0xff).toByte()
        wavHeader[26] = (sampleRate shr 16 and 0xff).toByte(); wavHeader[27] = (sampleRate shr 24 and 0xff).toByte()
        wavHeader[28] = (byteRate and 0xff).toByte(); wavHeader[29] = (byteRate shr 8 and 0xff).toByte()
        wavHeader[30] = (byteRate shr 16 and 0xff).toByte(); wavHeader[31] = (byteRate shr 24 and 0xff).toByte()
        wavHeader[32] = 2; wavHeader[33] = 0 
        wavHeader[34] = 16; wavHeader[35] = 0 
        wavHeader[36] = 'd'.code.toByte(); wavHeader[37] = 'a'.code.toByte(); wavHeader[38] = 't'.code.toByte(); wavHeader[39] = 'a'.code.toByte()
        wavHeader[40] = (byteData.size and 0xff).toByte(); wavHeader[41] = (byteData.size shr 8 and 0xff).toByte()
        wavHeader[42] = (byteData.size shr 16 and 0xff).toByte(); wavHeader[43] = (byteData.size shr 24 and 0xff).toByte()

        return wavHeader + byteData
    }
}
