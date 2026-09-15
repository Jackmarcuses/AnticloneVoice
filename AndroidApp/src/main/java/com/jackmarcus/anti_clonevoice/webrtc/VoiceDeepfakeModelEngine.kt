package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max

enum class ModelThreatLevel {
    GENUINE,
    SUSPICIOUS,
    CRITICAL_CLONE_ATTACK
}

data class ModelPrediction(
    val riskScore: Float, // 0.0 to 100.0%
    val threatLevel: ModelThreatLevel,
    val genuineProbability: Float,
    val deepfakeProbability: Float,
    val inferenceTimeMs: Long
)

class VoiceDeepfakeModelEngine(private val context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "DeepfakeModelEngine"
        private const val MODEL_NAME = "voice_deepfake_detector.tflite"
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            Log.i(TAG, "Initializing TensorFlow Lite Engine for Anti-Spoofing...")
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(2) // Parallel execution for low-latency target
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "TensorFlow Lite Model initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite model: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Executes real-time inference on the deepfake detector model using TensorFlow Lite.
     * Expects input representing 16kHz normalized mono audio PCM samples between -1.0f and 1.0f.
     *
     * @param audioData FloatArray of mono audio frames (e.g. size 8000 or 16000)
     */
    fun detectVoiceClone(audioData: FloatArray): ModelPrediction {
        val startTime = System.currentTimeMillis()
        val currentInterpreter = interpreter
        if (currentInterpreter == null || audioData.isEmpty()) {
            return ModelPrediction(0f, ModelThreatLevel.GENUINE, 1f, 0f, 0L)
        }

        try {
            // 1. Z-Score Normalization (Standardize audio data to improve model inference reliability)
            var sum = 0.0f
            for (value in audioData) sum += value
            val mean = sum / audioData.size

            var varianceSum = 0.0f
            for (value in audioData) {
                varianceSum += (value - mean) * (value - mean)
            }
            val stdDev = max(Math.sqrt((varianceSum / audioData.size).toDouble()).toFloat(), 1e-5f)

            // 2. Prepare ByteBuffer container allocating 4 bytes per float matching input shape [1, sample_count]
            val inputBuffer = ByteBuffer.allocateDirect(1 * audioData.size * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            for (value in audioData) {
                val normalizedValue = (value - mean) / stdDev
                inputBuffer.putFloat(normalizedValue)
            }
            inputBuffer.rewind()

            // 3. Prepare output container matching tensor shape [1, 2]
            val outputArray = Array(1) { FloatArray(2) }

            // 4. Run Model Inference
            currentInterpreter.run(inputBuffer, outputArray)

            // 5. Apply Softmax Activation Layer to logits outputs
            val logits = outputArray[0]
            val logitGenuine = logits[0]
            val logitDeepfake = logits[1]

            // Numerical stability subtraction helper
            val maxLogit = max(logitGenuine, logitDeepfake)
            val expGenuine = exp((logitGenuine - maxLogit).toDouble()).toFloat()
            val expDeepfake = exp((logitDeepfake - maxLogit).toDouble()).toFloat()
            val expSum = expGenuine + expDeepfake

            val genuineProb = expGenuine / expSum
            val deepfakeProb = expDeepfake / expSum
            val riskPercentage = deepfakeProb * 100.0f

            // 6. Calculate dynamic threat level status metrics
            val threatLevel = when {
                riskPercentage >= 60.0f -> ModelThreatLevel.CRITICAL_CLONE_ATTACK
                riskPercentage >= 30.0f -> ModelThreatLevel.SUSPICIOUS
                else -> ModelThreatLevel.GENUINE
            }

            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Inference completed in ${inferenceTime}ms -> Risk: ${String.format("%.2f", riskPercentage)}%, Threat: $threatLevel")

            return ModelPrediction(
                riskScore = riskPercentage,
                threatLevel = threatLevel,
                genuineProbability = genuineProb,
                deepfakeProbability = deepfakeProb,
                inferenceTimeMs = inferenceTime
            )

        } catch (e: Exception) {
            Log.e(TAG, "TensorFlow Lite inference execution error: ${e.message}")
            e.printStackTrace()
        }

        val fallbackTime = System.currentTimeMillis() - startTime
        return ModelPrediction(0f, ModelThreatLevel.GENUINE, 1f, 0f, fallbackTime)
    }

    override fun close() {
        try {
            Log.i(TAG, "Cleaning up TensorFlow Lite interpreter resources safely...")
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up model engine parameters: ${e.message}")
        }
    }
}
