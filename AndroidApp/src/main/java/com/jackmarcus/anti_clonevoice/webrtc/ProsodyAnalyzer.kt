package com.jackmarcus.anti_clonevoice.webrtc

import kotlin.math.abs
import kotlin.math.sqrt

data class ProsodyMetrics(
    val speechRate: Float,      // Syllables/Peaks per second
    val pitchVariance: Float,   // How much the pitch fluctuates (Jitter-like)
    val pauseRatio: Float,      // Fraction of silent frames
    val urgencyScore: Float     // 0-100 score based on speech rate and intensity
)

class ProsodyAnalyzer {

    /**
     * Analyzes 16kHz mono PCM data for behavioral patterns.
     */
    fun analyzeProsody(audioData: FloatArray, sampleRate: Int = 16000): ProsodyMetrics {
        if (audioData.isEmpty()) return ProsodyMetrics(0f, 0f, 0f, 0f)

        // 1. Calculate Speech Rate via Energy Peak Detection (Simple Syllable Proxy)
        val frameSize = 320 // 20ms at 16kHz
        var peakCount = 0
        var isAboveThreshold = false
        val energyThreshold = 0.01f
        
        var silentFrames = 0
        val totalFrames = audioData.size / frameSize
        val energies = mutableListOf<Float>()

        for (i in 0 until totalFrames) {
            val start = i * frameSize
            var frameEnergy = 0.0f
            for (j in 0 until frameSize) {
                frameEnergy += audioData[start + j] * audioData[start + j]
            }
            frameEnergy = sqrt(frameEnergy / frameSize)
            energies.add(frameEnergy)

            if (frameEnergy > energyThreshold) {
                if (!isAboveThreshold) {
                    peakCount++
                    isAboveThreshold = true
                }
            } else {
                isAboveThreshold = false
                silentFrames++
            }
        }

        val durationSeconds = audioData.size.toFloat() / sampleRate
        val speechRate = peakCount / durationSeconds
        val pauseRatio = silentFrames.toFloat() / totalFrames

        // 2. Pitch Variance (Rough Estimation using Zero Crossing Rate variance)
        val zcrs = mutableListOf<Float>()
        for (i in 0 until totalFrames) {
            val start = i * frameSize
            var crosses = 0
            for (j in 1 until frameSize) {
                if ((audioData[start + j] >= 0 && audioData[start + j - 1] < 0) ||
                    (audioData[start + j] < 0 && audioData[start + j - 1] >= 0)) {
                    crosses++
                }
            }
            zcrs.add(crosses.toFloat() / frameSize)
        }

        val meanZcr = zcrs.average().toFloat()
        val varianceZcr = zcrs.map { (it - meanZcr) * (it - meanZcr) }.average().toFloat()

        // 3. Urgency Detection: Combines high speech rate (> 5 syllables/sec) and low pause ratio
        val rateFactor = (speechRate / 6.0f).coerceIn(0f, 1f)
        val silenceFactor = (1.0f - pauseRatio).coerceIn(0f, 1f)
        val urgencyScore = (rateFactor * 0.6f + silenceFactor * 0.4f) * 100f

        return ProsodyMetrics(
            speechRate = speechRate,
            pitchVariance = varianceZcr * 1000f,
            pauseRatio = pauseRatio,
            urgencyScore = urgencyScore
        )
    }
}
