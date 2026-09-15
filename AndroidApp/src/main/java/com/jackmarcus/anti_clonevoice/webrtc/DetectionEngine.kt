package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import com.jackmarcus.anti_clonevoice.data.repository.TranscriptRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers

class DetectionEngine(
    private val context: Context,
    private val observer: WebRtcClient.WebRtcObserver,
    private val transcriptRepository: TranscriptRepository
) : AutoCloseable {
    private val TAG = "DetectionEngine"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val voiceGuard = VoiceGuardDetector()
    private val deepfakeModel = VoiceDeepfakeModelEngine(context)
    private val biometricEngine = VoiceBiometricEngine(context)
    private val prosodyAnalyzer = ProsodyAnalyzer()
    private val scamDetector = ScamContentDetector()
    private val llmAnalyzer = LlmScamAnalyzer(context)
    private val wav2vecDetector = Wav2VecDetector(context)
    private val cloudClient = CloudInferenceClient("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")
    private var cloudRiskScore = 0f
    
    private val transcriptionEngine = TranscriptionEngine(
        context,
        onTranscriptReady = { transcript -> updateTranscript(transcript) },
        onParagraphCompleted = { paragraph -> analyzeIntent(paragraph) }
    )

    init {
        transcriptionEngine.startListening()
        
        // Listen for language changes and notify the observer
        scope.launch {
            llmAnalyzer.detectedLanguage.collect { language ->
                observer.onLanguageDetected(language)
            }
        }
    }

    // Stored profile for comparison
    private var storedEmbedding: FloatArray? = null
    private var storedSpeechRate: Float = 0f
    private var storedPitchVariance: Float = 0f
    private var lastTranscript: String = ""

    fun setStoredProfile(embedding: FloatArray?, rate: Float, variance: Float) {
        this.storedEmbedding = embedding
        this.storedSpeechRate = rate
        this.storedPitchVariance = variance
    }

    /**
     * Updates the conversation transcript to be analyzed by the LLM.
     * This would typically be called by an ASR engine during a call.
     */
    fun updateTranscript(text: String) {
        lastTranscript = text
        observer.onTranscriptUpdated(text)
    }

    /**
     * Analyzes the intent of a full paragraph and saves it to the local history/database.
     */
    fun analyzeIntent(paragraph: String) {
        llmAnalyzer.analyzeParagraph(paragraph)
        
        // Save full paragraph to backend database only when it's complete
        scope.launch(Dispatchers.IO) {
            val language = llmAnalyzer.detectedLanguage.value
            val langToSave = if (language == "detecting...") "Unknown" else language
            Log.d(TAG, "Sending paragraph to DB ($langToSave): $paragraph")
            transcriptRepository.saveParagraph(paragraph, langToSave)
        }
        
        Log.i(TAG, "Intent Layer Analysis: ${llmAnalyzer.scamIntentMessage.value ?: "Safe"}")
    }

    fun processAudioWindow(pcmData: ShortArray, sampleRate: Int): Triple<Float, String, String> {
        val floatData = FloatArray(pcmData.size) { i -> pcmData[i] / 32768.0f }
        
        // 1. Voice Guard DSP Check
        val guardResult = voiceGuard.analyzeAudioChunk(pcmData)
        
        // 2. Neural Deepfake Check
        val modelResult = deepfakeModel.detectVoiceClone(floatData)

        // 3. Wav2Vec 2.0 Latent Anomaly Check
        val wav2vecScore = wav2vecDetector.analyzeLatentFeatures(floatData)
        
        // 4. Biometric Identity Check
        val liveEmbedding = biometricEngine.extractEmbedding(floatData)
        val identityScore = storedEmbedding?.let { 
            biometricEngine.calculateIdentityScore(liveEmbedding, it) 
        } ?: 1.0f // If no profile, assume match for baseline
        
        val identityMismatch = (1.0f - identityScore) * 100.0f

        // 4. Behavioral Prosody Check
        val prosodyMetrics = prosodyAnalyzer.analyzeProsody(floatData, sampleRate)
        val rateAnomaly = if (storedSpeechRate > 0) {
            Math.abs(prosodyMetrics.speechRate - storedSpeechRate) / storedSpeechRate * 100f
        } else 0f

        // 6. Risk Fusion Logic (Weighted)
        // AI_Score (max of DSP, Neural, Wav2Vec2, and Cloud API) * 0.35 + Identity_Mismatch * 0.25 + Pace_Anomaly * 0.1 + Content_Risk * 0.3
        
        // Trigger Cloud Inference in background (don't block the UI/audio thread)
        val audioBytes = pcmToWav(pcmData, sampleRate)
        scope.launch(Dispatchers.IO) {
            cloudRiskScore = cloudClient.getExactScamScore(audioBytes)
        }

        val aiScore = maxOf(guardResult.riskScore, modelResult.riskScore, wav2vecScore, cloudRiskScore)
        
        // Behavioral / Prosodic Urgency
        val urgencyRisk = prosodyMetrics.urgencyScore
        
        // Boost urgency importance if transcription is failing (Hinglish/Noisy environments)
        val weightedUrgency = if (lastTranscript.isEmpty()) urgencyRisk * 1.5f else urgencyRisk
        
        // Social Engineering / Content Heuristic (Hinglish/Multilingual keywords)
        val keywordResult = scamDetector.analyzeText(lastTranscript) 
        
        // LLM Intent Analysis (Foundation for Gemini Nano)
        val llmRisk = llmAnalyzer.llmRiskScore.value
        
        val contentRisk = maxOf(keywordResult.riskScore, llmRisk, (weightedUrgency - 30f).coerceAtLeast(0f))

        val finalRisk = (aiScore * 0.3f) + (identityMismatch * 0.2f) + 
                         (Math.min(rateAnomaly, 100f) * 0.1f) + (contentRisk * 0.4f)

        // Generate Alert Message
        val message = when {
            finalRisk >= 75 || contentRisk >= 80 -> "CRITICAL: Multilingual Scam Detected!"
            wav2vecScore >= 60 -> "CRITICAL: Wav2Vec2 Latent Anomaly Detected"
            finalRisk >= 60 -> "CRITICAL: Potential Voice Clone"
            llmRisk >= 70 -> "WARNING: LLM flagged suspicious intent"
            contentRisk >= 60 -> "WARNING: Suspicious Scam Content (Hinglish)"
            aiScore >= 50 -> "WARNING: Synthetic Voice Signature"
            identityMismatch >= 40 -> "WARNING: Identity Mismatch"
            urgencyRisk >= 70 -> "SUSPICIOUS: High Pressure Speech detected"
            rateAnomaly >= 50 -> "SUSPICIOUS: Unusual Talking Pace"
            else -> "Voice Verified: Secure"
        }
        
        val level = when {
            finalRisk >= 60 || contentRisk >= 60 || llmRisk >= 70 || wav2vecScore >= 60 -> "CRITICAL"
            finalRisk >= 30 || contentRisk >= 30 || urgencyRisk >= 70 -> "SUSPICIOUS"
            else -> "GENUINE"
        }

        Log.i(TAG, "Fusion Risk: ${String.format("%.2f", finalRisk)}% -> $message")
        return Triple(finalRisk, message, level)
    }

    override fun close() {
        scope.cancel()
        transcriptionEngine.stopListening()
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
        val byteRate = sampleRate * 2 // 16-bit mono

        val wavHeader = ByteArray(headerSize)
        // RIFF header
        wavHeader[0] = 'R'.code.toByte(); wavHeader[1] = 'I'.code.toByte(); wavHeader[2] = 'F'.code.toByte(); wavHeader[3] = 'F'.code.toByte()
        wavHeader[4] = (totalDataLen and 0xff).toByte(); wavHeader[5] = (totalDataLen shr 8 and 0xff).toByte()
        wavHeader[6] = (totalDataLen shr 16 and 0xff).toByte(); wavHeader[7] = (totalDataLen shr 24 and 0xff).toByte()
        // WAVE header
        wavHeader[8] = 'W'.code.toByte(); wavHeader[9] = 'A'.code.toByte(); wavHeader[10] = 'V'.code.toByte(); wavHeader[11] = 'E'.code.toByte()
        // fmt chunk
        wavHeader[12] = 'f'.code.toByte(); wavHeader[13] = 'm'.code.toByte(); wavHeader[14] = 't'.code.toByte(); wavHeader[15] = ' '.code.toByte()
        wavHeader[16] = 16; wavHeader[17] = 0; wavHeader[18] = 0; wavHeader[19] = 0 // format chunk size
        wavHeader[20] = 1; wavHeader[21] = 0 // format (PCM)
        wavHeader[22] = 1; wavHeader[23] = 0 // channels (mono)
        wavHeader[24] = (sampleRate and 0xff).toByte(); wavHeader[25] = (sampleRate shr 8 and 0xff).toByte()
        wavHeader[26] = (sampleRate shr 16 and 0xff).toByte(); wavHeader[27] = (sampleRate shr 24 and 0xff).toByte()
        wavHeader[28] = (byteRate and 0xff).toByte(); wavHeader[29] = (byteRate shr 8 and 0xff).toByte()
        wavHeader[30] = (byteRate shr 16 and 0xff).toByte(); wavHeader[31] = (byteRate shr 24 and 0xff).toByte()
        wavHeader[32] = 2; wavHeader[33] = 0 // block align
        wavHeader[34] = 16; wavHeader[35] = 0 // bits per sample
        // data chunk
        wavHeader[36] = 'd'.code.toByte(); wavHeader[37] = 'a'.code.toByte(); wavHeader[38] = 't'.code.toByte(); wavHeader[39] = 'a'.code.toByte()
        wavHeader[40] = (byteData.size and 0xff).toByte(); wavHeader[41] = (byteData.size shr 8 and 0xff).toByte()
        wavHeader[42] = (byteData.size shr 16 and 0xff).toByte(); wavHeader[43] = (byteData.size shr 24 and 0xff).toByte()

        return wavHeader + byteData
    }
}
