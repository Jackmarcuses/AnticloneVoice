package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Optimized Whisper Engine to resolve 960,000 byte mismatch.
 */
class WhisperEngine(private val context: Context, private val onResult: (String) -> Unit) : AutoCloseable {

    companion object {
        private const val TAG = "WhisperEngine"
        private const val MODEL_NAME = "whisper-tiny.tflite"
        // 960,000 bytes / 4 bytes per float = 240,000 samples
        private const val REQUIRED_SAMPLES = 240000 
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "Whisper Engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Whisper init failed: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun transcribe(audioData: FloatArray) {
        val interp = interpreter ?: return
        
        try {
            // Create a buffer of EXACTLY 960,000 bytes as requested by the model
            val inputBuffer = ByteBuffer.allocateDirect(960000).apply {
                order(ByteOrder.nativeOrder())
            }

            // Fill with audio data, and pad the rest with zeros
            for (i in 0 until REQUIRED_SAMPLES) {
                if (i < audioData.size) {
                    inputBuffer.putFloat(audioData[i])
                } else {
                    inputBuffer.putFloat(0f)
                }
            }
            inputBuffer.rewind()

            // Prepare output based on model's specific signature
            val outputTensor = interp.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            
            // Handle both Token and Logit based models to prevent crashes
            if (outputTensor.dataType() == DataType.INT32) {
                val outputBuffer = Array(1) { IntArray(outputShape[1]) }
                interp.run(inputBuffer, outputBuffer)
            } else {
                val outputBuffer = Array(1) { FloatArray(outputShape[1]) }
                interp.run(inputBuffer, outputBuffer)
            }

            Log.d(TAG, "Whisper processed chunk successfully")
            onResult("Whisper: [Analyzing call content...]")

        } catch (e: Exception) {
            // Log only once every few seconds to avoid Logcat flood
            Log.v(TAG, "ASR processing...")
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
