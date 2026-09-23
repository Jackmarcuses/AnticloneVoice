package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class VoiceBiometricEngine(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "VoiceBiometricEngine"
        private const val MODEL_NAME = "speaker_recognition.tflite"
        private const val EMBEDDING_SIZE = 192
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            Log.i(TAG, "Initializing Speaker Recognition Engine...")
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "Speaker Recognition Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load speaker recognition model: ${e.message}")
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
     * Extracts a 192-dimensional vector (Voice DNA) from a 3s audio chunk (16kHz).
     */
    fun extractEmbedding(audioData: FloatArray): FloatArray {
        val currentInterpreter = interpreter ?: return FloatArray(EMBEDDING_SIZE)
        
        try {
            // 1. Prepare Input Tensor [1, sample_count]
            val inputBuffer = ByteBuffer.allocateDirect(1 * audioData.size * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            for (value in audioData) {
                inputBuffer.putFloat(value)
            }
            inputBuffer.rewind()

            // 2. Prepare Output Tensor [1, 192]
            val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }

            // 3. Run Inference
            currentInterpreter.run(inputBuffer, outputArray)

            return outputArray[0]
        } catch (e: Exception) {
            Log.e(TAG, "Embedding extraction failed: ${e.message}")
            return FloatArray(EMBEDDING_SIZE)
        }
    }

    /**
     * Calculates Cosine Similarity between two voice vectors.
     * Range: -1.0 to 1.0 (Higher is more similar)
     */
    fun calculateIdentityScore(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.isEmpty() || vec2.isEmpty()) return 0.0f
        val minLen = minOf(vec1.size, vec2.size)
        if (minLen == 0) return 0.0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in 0 until minLen) {
            dotProduct += vec1[i] * vec2[i]
            normA += vec1[i] * vec1[i]
            normB += vec2[i] * vec2[i]
        }

        val denominator = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0.0f
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
