package com.termux.app.taskexecutor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Utility class for handling speech recognition
 */
class SpeechRecognitionUtil(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: ((Int) -> Unit)? = null,
    private val onPartialResult: ((String) -> Unit)? = null
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val LOG_TAG = "SpeechRecognitionUtil"
    
    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } else {
            Log.e(LOG_TAG, "Speech recognition not available")
        }
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(LOG_TAG, "Ready for speech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(LOG_TAG, "Beginning of speech")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Partial results received
            }
            
            override fun onEndOfSpeech() {
                Log.d(LOG_TAG, "End of speech")
            }
            
            override fun onError(error: Int) {
                Log.e(LOG_TAG, "Speech recognition error: $error")
                when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> Log.e(LOG_TAG, "Audio error")
                    SpeechRecognizer.ERROR_CLIENT -> Log.e(LOG_TAG, "Client error")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Log.e(LOG_TAG, "Insufficient permissions")
                    SpeechRecognizer.ERROR_NETWORK -> Log.e(LOG_TAG, "Network error")
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Log.e(LOG_TAG, "Network timeout")
                    SpeechRecognizer.ERROR_NO_MATCH -> Log.e(LOG_TAG, "No match")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Log.e(LOG_TAG, "Recognizer busy")
                    SpeechRecognizer.ERROR_SERVER -> Log.e(LOG_TAG, "Server error")
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Log.e(LOG_TAG, "Speech timeout")
                }
                onError?.invoke(error)
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.d(LOG_TAG, "Recognition result: $text")
                    onResult(text)
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.d(LOG_TAG, "Partial result: $text")
                    onPartialResult?.invoke(text) ?: onResult(text)
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // Event occurred
            }
        }
    }
    
    fun startListening() {
        if (speechRecognizer == null) {
            Log.e(LOG_TAG, "SpeechRecognizer not available")
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            Log.d(LOG_TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error starting speech recognition", e)
            onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
        }
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
        Log.d(LOG_TAG, "Stopped listening")
    }
    
    fun cancel() {
        speechRecognizer?.cancel()
        Log.d(LOG_TAG, "Cancelled recognition")
    }
    
    private fun restartListening() {
        if (speechRecognizer == null) return
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            Log.d(LOG_TAG, "Restarted listening")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error restarting speech recognition", e)
        }
    }
    
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(LOG_TAG, "Destroyed speech recognizer")
    }
    
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) && speechRecognizer != null
    }
}

