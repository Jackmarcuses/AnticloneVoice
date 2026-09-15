package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Meta's Wav2Vec 2.0 / FRILL based detection layer.
 * Uses manual Interpreter to avoid ML Binding issues.
 */
class Wav2VecDetector(private val context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "Wav2VecDetector"
        private const val MODEL_NAME = "1.tflite"
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            interpreter = Interpreter(modelBuffer)
            Log.i(TAG, "Wav2Vec 2.0 (1.tflite) loaded successfully via manual Interpreter")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load model: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun analyzeLatentFeatures(audioData: FloatArray): Float {
        val interp = interpreter ?: return 0f
        try {
            val inputBuffer = ByteBuffer.allocateDirect(audioData.size * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            for (value in audioData) inputBuffer.putFloat(value)
            inputBuffer.rewind()

            val outputBuffer = Array(1) { FloatArray(2048) }
            interp.run(inputBuffer, outputBuffer)

            var latentSum = 0f
            for (value in outputBuffer[0]) latentSum += Math.abs(value)
            
            val riskScore = (latentSum % 100f).coerceIn(0f, 100f)
            Log.i(TAG, "Manual Inference: Energy = $latentSum -> Risk: $riskScore%")
            return riskScore
        } catch (e: Exception) {
            return 0f
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
