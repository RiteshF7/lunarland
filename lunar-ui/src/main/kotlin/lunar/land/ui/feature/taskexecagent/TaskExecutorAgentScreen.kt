package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.TaskExecutorViewModel
import com.termux.app.taskexecutor.model.TaskExecutorMode
import com.termux.app.taskexecutor.model.TaskStatus
import com.termux.app.taskexecutor.ui.rememberVoiceInputHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * Main screen composable for the AI Agent Interaction Sphere.
 * This is the entry point that orchestrates all the UI components.
 */
@Composable
fun TaskExecutorAgentScreen(
    modifier: Modifier = Modifier,
    viewModel: TaskExecutorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentMode by remember { mutableStateOf(TaskExecutorMode.VOICE) }
    var isTextInputFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Voice input handler
    val context = androidx.compose.ui.platform.LocalContext.current
    var transcriptionText by remember { mutableStateOf("") }
    val (voiceInputHandler, isListening, _) = rememberVoiceInputHandler(
        context = context,
        onTextRecognized = { text -> 
            transcriptionText = text
            // Auto-execute command when voice input completes in voice mode
            if (currentMode == TaskExecutorMode.VOICE && text.isNotBlank() && uiState.taskStatus != TaskStatus.RUNNING) {
                viewModel.dispatchCommand(text)
                transcriptionText = ""
                // Keep transcription text briefly, then clear it
                scope.launch {
                    delay(2000)
                    transcriptionText = ""
                }
            }
        },
        onPartialResult = { text ->
            transcriptionText = text
        }
    )
    
    // Initialize service binding
    LaunchedEffect(Unit) {
        if (context is android.app.Activity) {
            viewModel.bindService(context)
        }
    }
    
    // Cleanup on dispose
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (context is android.app.Activity) {
                viewModel.unbindService(context)
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LunarTheme.BackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LunarTheme.Spacing.ExtraLarge, vertical = LunarTheme.Spacing.XXLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section with Debug Logs Button
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                PageHeader(
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Debug logs button in top-right corner
                DebugLogsButton(
                    showLogs = uiState.showLogs,
                    onToggle = { viewModel.toggleLogsVisibility() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                )
            }
            
            // Logs view (conditionally visible)
            AnimatedVisibility(
                visible = uiState.showLogs,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut() + androidx.compose.animation.scaleOut()
            ) {
                LogsView(
                    outputText = uiState.outputText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (currentMode == TaskExecutorMode.TEXT) {
                    // Text mode: Hide sphere, only show text input
                    TextInputPanel(
                        onExecute = { command ->
                            if (command.isNotBlank() && uiState.taskStatus != TaskStatus.RUNNING) {
                                viewModel.dispatchCommand(command)
                            }
                        },
                        onFocusChange = { focused ->
                            isTextInputFocused = focused
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                } else {
                    // Voice mode: Show sphere with status
                    SphereVisualizer(
                        modifier = Modifier.size(200.dp),
                        isListening = isListening,
                        taskStatus = uiState.taskStatus,
                        isTaskRunning = uiState.isTaskRunning,
                        onSphereClick = {
                            // Handle sphere click for voice input
                            if (uiState.taskStatus != TaskStatus.RUNNING) {
                                // Will be handled by pointer input below
                            }
                        }
                    )
                    
                    // Voice input gesture handler
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .pointerInput(uiState.taskStatus) {
                                if (uiState.taskStatus != TaskStatus.RUNNING) {
                                    detectTapGestures(
                                        onPress = { _ ->
                                            scope.launch {
                                                // Start voice input when pressed
                                                voiceInputHandler.startListening()
                                                // Wait for release
                                                tryAwaitRelease()
                                                // Stop voice input when released
                                                voiceInputHandler.stopListening()
                                            }
                                        }
                                    )
                                }
                            }
                    )
                    
                    // Show transcription text if available
                    if (transcriptionText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 220.dp)
                        ) {
                            Text(
                                text = transcriptionText,
                                style = LunarTheme.Typography.BodyMedium,
                                color = LunarTheme.TextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = LunarTheme.InactiveBackgroundColor.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(LunarTheme.CornerRadius.Medium)
                                    )
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Footer Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Toggle Button
                ModeToggleButton(
                    isTextMode = currentMode == TaskExecutorMode.TEXT,
                    onToggle = { 
                        currentMode = if (currentMode == TaskExecutorMode.TEXT) {
                            TaskExecutorMode.VOICE
                        } else {
                            TaskExecutorMode.TEXT
                        }
                        // Reset focus state when switching modes
                        isTextInputFocused = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
                
                // Status text
                StatusText(
                    text = uiState.statusText.ifEmpty { 
                        when (uiState.taskStatus) {
                            TaskStatus.STOPPED -> "Ready"
                            TaskStatus.RUNNING -> "Running..."
                            TaskStatus.SUCCESS -> "Success"
                            TaskStatus.ERROR -> "Error"
                        }
                    }
                )
                
                PageFooter(
                    modifier = Modifier.fillMaxWidth(),
                    aiStatus = uiState.statusText.ifEmpty { "Ready" },
                    isTextMode = currentMode == TaskExecutorMode.TEXT,
                    onTextModeClick = {}
                )
            }
        }
    }
}
