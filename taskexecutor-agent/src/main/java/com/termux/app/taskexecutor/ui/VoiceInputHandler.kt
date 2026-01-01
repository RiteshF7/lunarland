package com.termux.app.taskexecutor.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.termux.app.taskexecutor.SpeechRecognitionUtil
import com.termux.shared.logger.Logger

/**
 * Voice Input Handler
 * Manages speech recognition and audio permissions
 */
@Composable
fun rememberVoiceInputHandler(
    context: android.content.Context,
    onTextRecognized: (String) -> Unit,
    onPartialResult: ((String) -> Unit)? = null
): Triple<VoiceInputHandler, Boolean, String> {
    var isListening by remember { mutableStateOf(false) }
    var currentText by remember { mutableStateOf("") }
    
    val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Logger.logInfo("TaskExecutor", "Audio permission granted")
        } else {
            Logger.logError("TaskExecutor", "Audio permission denied")
        }
    }
    
    val speechRecognitionUtil = remember {
        SpeechRecognitionUtil(
            context = context,
            onResult = { text ->
                currentText = text
                onTextRecognized(text)
                Logger.logInfo("TaskExecutor", "Speech result: $text")
            },
            onError = { error ->
                isListening = false
                currentText = ""
                Logger.logError("TaskExecutor", "Speech recognition error: $error")
            },
            onPartialResult = { text ->
                currentText = text
                onPartialResult?.invoke(text)
                Logger.logInfo("TaskExecutor", "Partial result: $text")
            }
        )
    }
    
    // Cleanup speech recognition on dispose
    DisposableEffect(Unit) {
        onDispose {
            speechRecognitionUtil.destroy()
        }
    }
    
    val handler = remember {
        VoiceInputHandler(
            setIsListening = { isListening = it },
            hasAudioPermission = hasAudioPermission,
            requestPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            speechRecognitionUtil = speechRecognitionUtil
        )
    }
    
    return Triple(handler, isListening, currentText)
}

class VoiceInputHandler(
    private val setIsListening: (Boolean) -> Unit,
    private val hasAudioPermission: Boolean,
    private val requestPermission: () -> Unit,
    private val speechRecognitionUtil: SpeechRecognitionUtil
) {
    fun startListening() {
        // Check permission first
        if (!hasAudioPermission) {
            Logger.logInfo("TaskExecutor", "Requesting audio permission")
            requestPermission()
            return
        }
        
        if (speechRecognitionUtil.isAvailable()) {
            Logger.logInfo("TaskExecutor", "Starting speech recognition")
            speechRecognitionUtil.startListening()
            setIsListening(true)
        } else {
            Logger.logError("TaskExecutor", "Speech recognition not available")
        }
    }
    
    fun stopListening() {
        speechRecognitionUtil.stopListening()
        setIsListening(false)
        Logger.logInfo("TaskExecutor", "Stopped listening")
    }
}

