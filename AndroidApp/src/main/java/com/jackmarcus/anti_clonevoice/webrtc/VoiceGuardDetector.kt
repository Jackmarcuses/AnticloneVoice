package com.jackmarcus.anti_clonevoice.webrtc

import android.util.Log
import kotlin.math.*

enum class ThreatLevel {
    GENUINE,
    SUSPICIOUS,
    CRITICAL
}

data class DetectionResult(
    val riskScore: Float,
    val threatLevel: ThreatLevel,
    val neuralVocoderScore: Float,
    val jitterAnomalyScore: Float,
    val spectralFlatness: Float,
    val pitchF0Hz: Float,
    val isSpeechActive: Boolean,
    val inferenceTimeMs: Long
)

class VoiceGuardDetector {

    companion object {
        private const val TAG = "VoiceGuardDetector"
        private const val SAMPLE_RATE = 16000
        private const val MIN_RMS = 0.005f
        private const val EMA_ALPHA = 0.45f
        
        // Human pitch F0 range in terms of frequency (Hz)
        private const val MIN_F0 = 50.0
        private const val MAX_F0 = 500.0
        
        // Autocorrelation lag bounds for 16kHz sample rate
        private const val MAX_LAG = (SAMPLE_RATE / MIN_F0).toInt() // 320 samples
        private const val MIN_LAG = (SAMPLE_RATE / MAX_F0).toInt() // 32 samples
    }

    private var smoothedRisk: Float = 0.0f

    /**
     * Resets the temporal exponential moving average (EMA) filter state.
     */
    fun resetState() {
        smoothedRisk = 0.0f
    }

    /**
     * Analyzes a 16kHz mono audio chunk to identify deepfake or synthetic signatures.
     * High performance logic optimized to run significantly below the 5ms timeline target.
     */
    fun analyzeAudioChunk(shorts: ShortArray): DetectionResult {
        val startTime = System.nanoTime()

        // 1. Voice Activity Check via RMS
        var squaredSum = 0.0
        for (s in shorts) {
            val normalized = s / 32768.0
            squaredSum += normalized * normalized
        }
        val rms = sqrt(squaredSum / shorts.size)

        if (rms < MIN_RMS) {
            smoothedRisk *= 0.8f
            if (smoothedRisk < 0.1f) smoothedRisk = 0.0f
            val inferenceTime = (System.nanoTime() - startTime) / 1000000
            return DetectionResult(
                riskScore = smoothedRisk,
                threatLevel = getThreatLevel(smoothedRisk),
                neuralVocoderScore = 0.0f,
                jitterAnomalyScore = 0.0f,
                spectralFlatness = 0.0f,
                pitchF0Hz = 0.0f,
                isSpeechActive = false,
                inferenceTimeMs = inferenceTime
            )
        }

        // Convert Shorts to a float range normalized array for pure DSP operations
        val floatData = FloatArray(shorts.size) { i -> shorts[i] / 32768.0f }

        // 2. Spectral Analysis (FFT, Wiener Flatness, High-Frequency Ratio)
        // Find next highest power of 2 size for Radix-2 algorithm compatibility
        var fftSize = 1
        while (fftSize < floatData.size) {
            fftSize = fftSize shl 1
        }
        
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        System.arraycopy(floatData, 0, real, 0, floatData.size)

        // Apply a gentle Hamming window to remove frame edge discontinuity leakage
        for (i in 0 until floatData.size) {
            val windowMultiplier = 0.54f - 0.46f * cos(2.0f * PI.toFloat() * i / (floatData.size - 1))
            real[i] *= windowMultiplier
        }

        // Run internal core Radix-2 FFT
        computeFft(real, imag, fftSize)

        // Calculate half-spectrum power profile
        val halfSize = fftSize / 2
        val powerSpectrum = FloatArray(halfSize)
        var powerSum = 0.0f
        var logPowerSum = 0.0

        var highFreqEnergy = 0.0f
        var totalSpectrumEnergy = 0.0f

        val hzPerBin = SAMPLE_RATE.toFloat() / fftSize

        for (i in 0 until halfSize) {
            val magnitudeSquared = (real[i] * real[i]) + (imag[i] * imag[i])
            powerSpectrum[i] = magnitudeSquared
            powerSum += magnitudeSquared
            logPowerSum += ln(max(magnitudeSquared.toDouble(), 1e-12))

            val currentHz = i * hzPerBin
            if (currentHz >= 3000.0f) {
                highFreqEnergy += magnitudeSquared
            }
            totalSpectrumEnergy += magnitudeSquared
        }

        // Wiener Flatness Calculation
        val geometricMean = exp(logPowerSum / halfSize)
        val arithmeticMean = (powerSum / halfSize) + 1e-12f
        val spectralFlatness = (geometricMean / arithmeticMean).toFloat()

        // High Frequency Buzz Metric
        val highFreqRatio = if (totalSpectrumEnergy > 0f) highFreqEnergy / totalSpectrumEnergy else 0f

        // Score components for neural vocoders (VITS, ElevenLabs, HiFi-GAN phase anomalies)
        var vocoderScore = 0.0f
        if (spectralFlatness > 0.008f) vocoderScore += 50.0f
        if (highFreqRatio > 0.25f) vocoderScore += 50.0f
        vocoderScore = min(vocoderScore, 100.0f)


        // 3. Pitch (F0) Tracking & Jitter Metrics via Autocorrelation
        val numSubFrames = 4
        val subFrameSize = floatData.size / numSubFrames
        val subFrameF0s = DoubleArray(numSubFrames)
        var activeSubFrames = 0

        for (f in 0 until numSubFrames) {
            val startIdx = f * subFrameSize
            val f0 = computeSubFrameF0(floatData, startIdx, subFrameSize)
            subFrameF0s[f] = f0
            if (f0 in MIN_F0..MAX_F0) {
                activeSubFrames++
            }
        }

        // Calculate mean pitch and sample micro-variance (jitter coefficients)
        var meanF0 = 0.0
        var jitterAnomaly = 0.0f
        var finalPitch = 0.0f

        if (activeSubFrames >= 2) {
            var sumF0 = 0.0
            for (f in 0 until numSubFrames) {
                if (subFrameF0s[f] in MIN_F0..MAX_F0) sumF0 += subFrameF0s[f]
            }
            meanF0 = sumF0 / activeSubFrames
            finalPitch = meanF0.toFloat()

            // Compute pitch variation between steps
            var varianceSum = 0.0
            for (f in 0 until numSubFrames) {
                if (subFrameF0s[f] in MIN_F0..MAX_F0) {
                    varianceSum += (subFrameF0s[f] - meanF0) * (subFrameF0s[f] - meanF0)
                }
            }
            val standardDeviation = sqrt(varianceSum / activeSubFrames)
            val jitter = (standardDeviation / meanF0).toFloat() // Normalized coefficient

            // GOOGLE TRANSLATE / BASIC TTS DETECTION:
            // Natural human speech ALWAYS has small variations (jitter).
            // Basic TTS engines (like Google Translate) often have "perfectly flat" pitch in short windows.
            if (jitter < 0.005f) {
                jitterAnomaly = 100.0f // Absolute synthetic flatness detected
            } else if (jitter < 0.015f) {
                jitterAnomaly = 85.0f  // Very high probability of high-end TTS
            } else if (jitter > 0.35f) {
                jitterAnomaly = 90.0f  // Phase generation artifact / Vocoder glitch
            } else {
                // Scale linearly into safer ranges
                jitterAnomaly = (jitter / 0.35f) * 15.0f
            }
        } else {
            // Unpitched or unvoiced sounds
            jitterAnomaly = 0.0f
        }

        val spectralFlatnessAnomaly = min((spectralFlatness / 0.02f) * 100.0f, 100.0f)


        // 4. Composite Risk Matrix Scoring & Temporal Filter Smoothing
        val rawScore = (vocoderScore * 0.55f) + (jitterAnomaly * 0.30f) + (spectralFlatnessAnomaly * 0.15f)
        
        // Apply EMA filter
        smoothedRisk = (EMA_ALPHA * rawScore) + ((1.0f - EMA_ALPHA) * smoothedRisk)
        smoothedRisk = max(0.0f, min(100.0f, smoothedRisk))

        val inferenceTimeMs = (System.nanoTime() - startTime) / 1000000

        return DetectionResult(
            riskScore = smoothedRisk,
            threatLevel = getThreatLevel(smoothedRisk),
            neuralVocoderScore = vocoderScore,
            jitterAnomalyScore = jitterAnomaly,
            spectralFlatness = spectralFlatness,
            pitchF0Hz = finalPitch,
            isSpeechActive = true,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    private fun computeSubFrameF0(data: FloatArray, start: Int, size: Int): Double {
        var bestLag = -1
        var bestR = -1.0
        
        // Zero-lag energy profile computation
        var r0 = 0.0
        for (i in 0 until size) {
            val idx = start + i
            if (idx >= data.size) break
            r0 += data[idx] * data[idx]
        }
        if (r0 < 1e-6) return 0.0

        // Search lags across the configured human pitch boundaries
        for (lag in MIN_LAG..MAX_LAG) {
            var rLag = 0.0
            for (i in 0 until (size - lag)) {
                val idx1 = start + i
                val idx2 = start + i + lag
                if (idx2 >= data.size) break
                rLag += data[idx1] * data[idx2]
            }
            
            // Normalized correlation factor
            val normalizedR = rLag / r0
            if (normalizedR > bestR) {
                bestR = normalizedR
                bestLag = lag
            }
        }

        return if (bestLag != -1 && bestR > 0.35) {
            SAMPLE_RATE.toDouble() / bestLag
        } else {
            0.0
        }
    }

    private fun getThreatLevel(score: Float): ThreatLevel {
        return when {
            score >= 60.0f -> ThreatLevel.CRITICAL
            score >= 30.0f -> ThreatLevel.SUSPICIOUS
            else -> ThreatLevel.GENUINE
        }
    }

    /**
     * Pure in-class high performance Radix-2 Cooley-Tukey FFT Implementation.
     * Operates completely in-place avoiding garbage collection allocations.
     */
    private fun computeFft(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation loop
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var m = n shr 1
            while (m in 1..j) {
                j = j - m
                m = m shr 1
            }
            j += m
        }

        // Main stage Cooley-Tukey butterfly iteration layer
        var size = 2
        while (size <= n) {
            val halfSize = size shr 1
            val tabStep = n / size
            
            for (i in 0 until n step size) {
                for (k in 0 until halfSize) {
                    val kTab = k * tabStep
                    // Calculate precise sine/cosine angles on the fly
                    val angle = -2.0 * PI * kTab / n
                    val wr = cos(angle).toFloat()
                    val wi = sin(angle).toFloat()

                    val matchIdx = i + k
                    val butterflyIdx = matchIdx + halfSize

                    val tr = (real[butterflyIdx] * wr) - (imag[butterflyIdx] * wi)
                    val ti = (real[butterflyIdx] * wi) + (imag[butterflyIdx] * wr)

                    real[butterflyIdx] = real[matchIdx] - tr
                    imag[butterflyIdx] = imag[matchIdx] - ti

                    real[matchIdx] += tr
                    imag[matchIdx] += ti
                }
            }
            size = size bshl 1 // Move up to next stage sizing bounds
        }
    }
    
    // Safety extension function backport helper for Bitwise Shift operation safety
    private infix fun Int.bshl(x: Int): Int = this shl x
}
