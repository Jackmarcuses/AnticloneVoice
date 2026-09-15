package com.jackmarcus.anti_clonevoice.webrtc

import android.util.Log

import java.util.Locale

class ScamContentDetector {
    private val TAG = "ScamContentDetector"

    private val highRiskKeywords = listOf(
        // English
        "money", "transfer", "wire", "bank", "account", "password", "gift card",
        "crypto", "bitcoin", "urgent", "police", "emergency", "jail", "accident",
        "relative", "hospital", "payment", "verification", "code", "otp", "kyc",
        // Hindi (Romanized)
        "paisa", "paise", "jaldi", "turant", "khatre", "darr", "police", "jail",
        "accident", "lottery", "prize", "gift", "namaste", "beta", "rishi",
        // Hindi (Devanagari)
        "पैसे", "पैसा", "जल्दी", "तुरंत", "खतरे", "डर", "पुलिस", "जेल",
        "एक्सीडेंट", "लॉटरी", "इनाम", "गिफ्ट", "नमस्ते", "बेटा"
    )

    private val socialEngineeringPatterns = listOf(
        // English
        "don't hang up", "stay on the line", "keep this secret", "do not tell anyone",
        "immediate action required", "your account is compromised", "verify your identity",
        // Hindi (Romanized)
        "call mat katna", "kisi ko mat batana", "secret rakho", "account block ho jayega",
        "turant paisa bhejo", "aapki madad chahiye",
        // Hindi (Devanagari)
        "कॉल मत काटना", "किसी को मत बताना", "सीक्रेट रखो", "अकाउंट ब्लॉक हो जाएगा",
        "तुरंत पैसा भेजो", "आपकी मदद चाहिए"
    )

    private val safeKeywords = listOf(
        "vlog", "subscribe", "beautiful", "view", "hotel", "travel", "food", "explore",
        "weather", "holiday", "vacation", "like and share", "comment below"
    )

    /**
     * Analyzes transcribed text for scam indicators with context awareness.
     */
    fun analyzeText(text: String): ScamDetectionResult {
        val lowercaseText = text.lowercase(Locale.US)
        var scamMatches = 0
        var safeMatches = 0
        val foundKeywords = mutableListOf<String>()

        highRiskKeywords.forEach { if (lowercaseText.contains(it)) { scamMatches++; foundKeywords.add(it) } }
        socialEngineeringPatterns.forEach { if (lowercaseText.contains(it)) { scamMatches += 2; foundKeywords.add(it) } }
        safeKeywords.forEach { if (lowercaseText.contains(it)) safeMatches++ }

        // Smart logic: If safe words (vlog style) are present, significantly reduce risk
        val adjustedMatches = (scamMatches - (safeMatches * 2)).coerceAtLeast(0)

        val riskScore = when {
            adjustedMatches >= 4 -> 90f
            adjustedMatches >= 2 -> 60f
            adjustedMatches >= 1 -> 30f
            else -> 0f
        }

        val threatLevel = when {
            riskScore >= 60 -> ThreatLevel.CRITICAL
            riskScore >= 30 -> ThreatLevel.SUSPICIOUS
            else -> ThreatLevel.GENUINE
        }

        if (adjustedMatches > 0) {
            Log.w(TAG, "Scam keywords detected: $foundKeywords. Risk Score: $riskScore")
        }

        return ScamDetectionResult(riskScore, threatLevel, foundKeywords)
    }
}

data class ScamDetectionResult(
    val riskScore: Float,
    val threatLevel: ThreatLevel,
    val matchedKeywords: List<String>
)
