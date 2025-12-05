package com.termux.app.taskexecutor.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.termux.app.taskexecutor.model.TaskExecutorMode
import com.termux.app.taskexecutor.ui.components.*
import com.termux.app.TaskExecutorViewModel
import com.termux.app.TaskExecutorViewModelFactory
import com.termux.app.TermuxService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Main Task Executor Screen Composable
 * Orchestrates all components and manages state
 */
@Composable
fun TaskExecutorScreen(
    modifier: Modifier = Modifier,
    showLogsButton: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    
    val viewModel: TaskExecutorViewModel = remember(activity) {
        val factory = TaskExecutorViewModelFactory(activity)
        ViewModelProvider(activity as ViewModelStoreOwner, factory)[TaskExecutorViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    var commandText by remember { mutableStateOf("") }
    var currentMode by remember { mutableStateOf(TaskExecutorMode.TEXT) }
    val scope = rememberCoroutineScope()
    val buttonColor = MaterialTheme.colorScheme.primary
    
    // Voice input handler
    val (voiceInputHandler, isListening) = rememberVoiceInputHandler(
        context = context,
        onTextRecognized = { text -> 
            commandText = text
            // Auto-execute command when voice input completes in voice mode
            if (currentMode == TaskExecutorMode.VOICE && text.isNotBlank()) {
                viewModel.dispatchCommand(text)
                commandText = ""
            }
        }
    )
    
    // Initialize service binding
    LaunchedEffect(Unit) {
        if (context is Activity) {
            viewModel.bindService(context)
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (context is Activity) {
                viewModel.unbindService(context)
                TermuxService.setTaskExecutorState(null, 0, false)
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode toggle button and logs toggle button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode toggle button
            ModeToggleButton(
                currentMode = currentMode,
                onModeChange = { currentMode = it }
            )
            
            // Logs toggle button (configurable)
            if (showLogsButton) {
                LogsToggleButton(
                    showLogs = uiState.showLogs,
                    onToggle = { viewModel.toggleLogsVisibility() }
                )
            }
        }
        
        // Logs view (conditionally visible)
        if (uiState.showLogs) {
            LogsView(
                outputText = uiState.outputText,
                buttonColor = buttonColor
            )
        }
        
        // Content based on mode
        when (currentMode) {
            TaskExecutorMode.TEXT -> {
                // Text input mode
                CommandInputField(
                    commandText = commandText,
                    onCommandTextChange = { commandText = it },
                    onExecute = {
                        if (commandText.isNotBlank()) {
                            viewModel.dispatchCommand(commandText)
                            commandText = ""
                        }
                    },
                    taskStatus = uiState.taskStatus,
                    isListening = isListening,
                    onStartListening = { voiceInputHandler.startListening() },
                    onStopListening = { voiceInputHandler.stopListening() },
                    scope = scope,
                    buttonColor = buttonColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TaskExecutorMode.VOICE -> {
                // Voice-only mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    VoiceOnlyUI(
                        isListening = isListening,
                        onStartListening = { voiceInputHandler.startListening() },
                        onStopListening = { voiceInputHandler.stopListening() },
                        scope = scope,
                        buttonColor = buttonColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

