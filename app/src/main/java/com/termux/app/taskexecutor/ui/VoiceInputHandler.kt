package com.termux.app.taskexecutor.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.termux.app.SpeechRecognitionUtil
import com.termux.shared.logger.Logger

/**
 * Voice Input Handler
 * Manages speech recognition and audio permissions
 */
@Composable
fun rememberVoiceInputHandler(
    context: android.content.Context,
    onTextRecognized: (String) -> Unit
): Pair<VoiceInputHandler, Boolean> {
    var isListening by remember { mutableStateOf(false) }
    
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
                onTextRecognized(text)
                Logger.logInfo("TaskExecutor", "Speech result: $text")
            },
            onError = { error ->
                isListening = false
                Logger.logError("TaskExecutor", "Speech recognition error: $error")
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
    
    return Pair(handler, isListening)
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

