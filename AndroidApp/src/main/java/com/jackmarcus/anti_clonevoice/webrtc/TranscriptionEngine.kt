package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Real-time Speech-to-Text engine to feed the LLM analysis.
 * Supports Multilingual input (Hindi, English, etc.) via Android Speech API.
 */
class TranscriptionEngine(
    private val context: Context,
    private val onTranscriptReady: (String) -> Unit,
    private val onParagraphCompleted: (String) -> Unit
) {
    private val TAG = "TranscriptionEngine"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val paragraphBuffer = StringBuilder()

    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("en-IN", "hi-IN"))
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
    }

    fun startListening() {
        if (isListening) return
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech Recognition not available")
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createListener())
        }
        
        try {
            speechRecognizer?.startListening(recognizerIntent)
            isListening = true
            Log.d(TAG, "Started listening for transcription")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}")
        }
    }

    private fun restartWithDelay(isError: Boolean = false) {
        if (!isListening && speechRecognizer != null) {
            val delay = if (isError) 10000L else 2000L
            Handler(Looper.getMainLooper()).postDelayed({
                if (speechRecognizer != null) startListening()
            }, delay)
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Transcription ready")
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
            restartWithDelay(false)
        }
        override fun onError(error: Int) {
            isListening = false
            Log.w(TAG, "Transcription error: $error")
            val isBusy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_AUDIO
            restartWithDelay(isBusy)
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.i(TAG, "Transcription Result: $text")
                onTranscriptReady(text)
                
                // Final result usually completes a paragraph
                paragraphBuffer.append(text).append(". ")
                onParagraphCompleted(paragraphBuffer.toString())
                paragraphBuffer.clear()
            }
            restartWithDelay(false)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                onTranscriptReady(text)
                
                // If the partial text ends with punctuation, treat it as a paragraph break
                if (text.endsWith(".") || text.endsWith("?") || text.endsWith("!")) {
                    paragraphBuffer.append(text).append(" ")
                    onParagraphCompleted(paragraphBuffer.toString())
                    paragraphBuffer.clear()
                }
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
