package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Advanced Intent Analyzer for call transcripts.
 * Uses Google ML Kit for dynamic language detection and handles paragraph-level intent.
 */
class LlmScamAnalyzer(private val context: Context) {
    private val TAG = "LlmScamAnalyzer"
    private var languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()

    private val _llmRiskScore = MutableStateFlow(0f)
    val llmRiskScore: StateFlow<Float> = _llmRiskScore.asStateFlow()

    private val _scamIntentMessage = MutableStateFlow<String?>(null)
    val scamIntentMessage: StateFlow<String?> = _scamIntentMessage.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("detecting...")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    /**
     * Analyzes full paragraphs for scam intent and identifies the language.
     */
    fun analyzeParagraph(paragraph: String) {
        if (paragraph.isEmpty()) return

        // 1. Detect Language Dynamically
        languageIdentifier.identifyLanguage(paragraph)
            .addOnSuccessListener { languageCode ->
                _detectedLanguage.value = when (languageCode) {
                    "hi" -> "Hindi"
                    "en" -> "English"
                    "und" -> "Unknown"
                    else -> languageCode.uppercase()
                }
                Log.d(TAG, "Detected Language: $languageCode")
            }
            .addOnFailureListener {
                Log.e(TAG, "Language detection failed: ${it.message}")
            }

        // 2. Intent Analysis (Paragraph-level)
        Log.i(TAG, "Analyzing Intent of Paragraph: \"$paragraph\"")
        
        val lowerText = paragraph.lowercase()
        
        // Advanced Intent Heuristics (Social Engineering Detection)
        val hasFinancialIntent = lowerText.contains("paisa") || lowerText.contains("money") || lowerText.contains("transfer")
        val hasUrgencyIntent = lowerText.contains("jaldi") || lowerText.contains("urgent") || lowerText.contains("immediately")
        val hasThreatIntent = lowerText.contains("police") || lowerText.contains("jail") || lowerText.contains("account block")
        
        if (hasFinancialIntent && (hasUrgencyIntent || hasThreatIntent)) {
            _llmRiskScore.value = 90f
            _scamIntentMessage.value = "CRITICAL: Scammer detected asking for money with urgency."
        } else if (hasUrgencyIntent && hasThreatIntent) {
            _llmRiskScore.value = 75f
            _scamIntentMessage.value = "WARNING: Detected high-pressure threat intent."
        } else {
            // Decay risk for safe paragraphs
            _llmRiskScore.value = (_llmRiskScore.value * 0.5f).coerceAtLeast(0f)
        }
    }

    fun reset() {
        _llmRiskScore.value = 0f
        _scamIntentMessage.value = null
        _detectedLanguage.value = "detecting..."
    }
}
