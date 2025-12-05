package com.termux.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import lunar.land.ui.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.termux.BuildConfig
import com.termux.shared.logger.Logger
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession.TermuxSessionClient
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.app.TermuxInstaller
import java.util.concurrent.atomic.AtomicInteger

/**
 * Composable ViewModel for Task Executor
 */
enum class TaskStatus {
    STOPPED,
    RUNNING,
    SUCCESS,
    ERROR
}

data class TaskExecutorComposableUiState(
    val statusText: String = "",
    val outputText: String = "",
    val isUiEnabled: Boolean = false,
    val sessionFinished: Boolean = false,
    val exitCode: Int? = null,
    val currentTask: String? = null,
    val taskProgress: Int = 0,
    val isTaskRunning: Boolean = false,
    val showLogs: Boolean = false,
    val taskStatus: TaskStatus = TaskStatus.STOPPED
)

class TaskExecutorComposableViewModelFactory(private val activity: Activity) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TaskExecutorComposableViewModel(activity) as T
    }
}

class TaskExecutorComposableViewModel(private val activity: Activity) : AndroidViewModel(activity.application) {
    
    private val LOG_TAG = "TaskExecutorComposableViewModel"
    
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(TaskExecutorComposableUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<TaskExecutorComposableUiState> = _uiState.asStateFlow()
    
    private val sessionIds = AtomicInteger()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    var termuxService: TermuxService? = null
    var currentSession: TermuxSession? = null
    var terminalSession: TerminalSession? = null
    private var sessionClient: TaskExecutorComposableSessionClient? = null
    var sessionFinished = false
    var isServiceBound = false
    var currentTaskCommand: String? = null
    var taskStartTime: Long = 0
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Logger.logDebug(LOG_TAG, "onServiceConnected")
            termuxService = (service as TermuxService.LocalBinder).service
            prepareSession()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Logger.logDebug(LOG_TAG, "onServiceDisconnected")
            termuxService = null
        }
    }
    
    fun bindService(context: Context) {
        if (isServiceBound) return
        
        try {
            val serviceIntent = Intent(context, TermuxService::class.java)
            context.startService(serviceIntent)
            
            if (!context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                throw RuntimeException("bindService() failed")
            }
            isServiceBound = true
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start TermuxService", e)
            updateStatus("Failed to start service: ${e.message}")
        }
    }
    
    fun unbindService(context: Context) {
        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // ignore
            }
            isServiceBound = false
        }
    }
    
    private fun prepareSession() {
        updateStatus("Setting up environment...")
        setUiEnabled(false)
        TermuxInstaller.setupBootstrapIfNeeded(activity) {
            mainHandler.post {
                startNewSession()
            }
        }
    }
    
    private fun startNewSession() {
        if (termuxService == null) {
            Logger.logError(LOG_TAG, "TermuxService not available")
            updateStatus("Service not available")
            return
        }
        
        tearDownSession()
        val client = TaskExecutorComposableSessionClient { transcript ->
            mainHandler.post {
                updateOutput(transcript)
            }
        }
        sessionClient = client
        
        val executionCommand = ExecutionCommand(sessionIds.incrementAndGet())
        executionCommand.runner = Runner.TERMINAL_SESSION.getName()
        executionCommand.shellName = "taskexecutor"
        executionCommand.commandLabel = "Task Executor Shell"
        executionCommand.workingDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH
        executionCommand.setShellCommandShellEnvironment = true
        executionCommand.terminalTranscriptRows = 4000
        executionCommand.shellCreateMode = ExecutionCommand.ShellCreateMode.ALWAYS.getMode()
        
        currentSession = termuxService!!.createTermuxSession(executionCommand)
        
        if (currentSession == null) {
            Logger.logError(LOG_TAG, "Failed to create session via service, trying direct creation")
            currentSession = TermuxSession.execute(
                termuxService!!,
                executionCommand,
                client,
                object : TermuxSessionClient {
                    override fun onTermuxSessionExited(session: TermuxSession) {
                        val exitCode = session.executionCommand.resultData.exitCode
                        mainHandler.post {
                            sessionFinished = true
                            val code = exitCode ?: -1
                            setSessionFinished(true, code)
                            updateStatus("Session finished (exit $code)")
                        }
                    }
                },
                TermuxShellEnvironment(),
                null,
                false
            )
            if (currentSession != null) {
                try {
                    val shellManagerField = TermuxService::class.java.getDeclaredField("mShellManager")
                    shellManagerField.isAccessible = true
                    val shellManager = shellManagerField.get(termuxService)
                    val sessionsField = shellManager.javaClass.getDeclaredField("mTermuxSessions")
                    sessionsField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val sessions = sessionsField.get(shellManager) as MutableList<Any>
                    sessions.add(currentSession!!)
                    Logger.logInfo(LOG_TAG, "Manually added session to service manager")
                } catch (e: Exception) {
                    Logger.logError(LOG_TAG, "Failed to add session to service manager: ${e.message}")
                }
            }
        } else {
            currentSession!!.getTerminalSession().updateTerminalSessionClient(client)
        }
        
        if (currentSession == null) {
            Logger.logError(LOG_TAG, "Failed to create task executor session")
            mainHandler.post {
                updateStatus("Unable to start shell")
            }
            return
        }
        
        terminalSession = currentSession!!.getTerminalSession()
        sessionFinished = false
        resetSessionState()
        
        try {
            terminalSession!!.initializeEmulator(80, 24, 8, 16)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Unable to initialize terminal emulator", e)
            mainHandler.post {
                updateStatus("Error: ${e.message ?: "Unknown error"}")
            }
            return
        }
        
        mainHandler.post {
            setUiEnabled(true)
            updateTaskState(null, 0, false, TaskStatus.STOPPED)
        }
        client.refreshTranscript(terminalSession!!)
        
        setupAdbEnvironment()
        setupGoogleApiKey()
    }
    
    private fun setupAdbEnvironment() {
        if (terminalSession == null || sessionFinished) return
        
        Thread {
            try {
                val adbSetupCommand = """
                    export TMPDIR="${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"
                    mkdir -p "${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"
                    export ANDROID_SERIAL="127.0.0.1:5558"
                    # Start ADB server
                    adb start-server 2>/dev/null || true
                    # Connect ADB to localhost
                    adb connect 127.0.0.1:5558 2>/dev/null || true
                    # Wait for connection to establish
                    sleep 1
                    # Verify ADB connection
                    if adb devices | grep -q "127.0.0.1:5558.*device"; then
                        echo "✓ ADB connected to localhost:5558"
                    else
                        echo "⚠ ADB connection to localhost:5558 not ready (will retry on command execution)"
                    fi
                    if ! grep -q "export TMPDIR" "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc" 2>/dev/null; then
                        echo 'export TMPDIR="${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"' >> "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                        mkdir -p "${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"
                    fi
                    if ! grep -q "export ANDROID_SERIAL" "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc" 2>/dev/null; then
                        echo 'export ANDROID_SERIAL="127.0.0.1:5558"' >> "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                    fi
                    echo "ADB environment configured"
                """.trimIndent()
                
                mainHandler.post {
                    if (terminalSession != null && !sessionFinished) {
                        terminalSession!!.write(adbSetupCommand)
                        terminalSession!!.write("\n")
                        Logger.logInfo(LOG_TAG, "Setting up ADB environment")
                    }
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error setting up ADB environment", e)
            }
        }.start()
    }
    
    private fun setupGoogleApiKey() {
        if (terminalSession == null || sessionFinished) return
        
        Thread {
            try {
                val apiKeyFromProps = BuildConfig.GOOGLE_API_KEY
                
                if (apiKeyFromProps.isBlank()) {
                    mainHandler.post {
                        updateStatus("GOOGLE_API_KEY not found in local.properties")
                        Logger.logError(LOG_TAG, "GOOGLE_API_KEY not found in local.properties")
                    }
                    return@Thread
                }
                
                val profilePath = "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                val apiKeyVar = "GOOGLE_API_KEY"
                val telemetryVar = "DROIDRUN_TELEMETRY_ENABLED"
                val setupCommand = """
                    if [ -z "$$apiKeyVar" ]; then
                        export $apiKeyVar="$apiKeyFromProps"
                        if ! grep -q "export $apiKeyVar" $profilePath 2>/dev/null; then
                            echo 'export $apiKeyVar="$apiKeyFromProps"' >> $profilePath
                        fi
                        echo "$apiKeyVar exported from local.properties"
                    else
                        echo "$apiKeyVar already set in environment"
                    fi
                    export $telemetryVar=false
                    if ! grep -q "export $telemetryVar" $profilePath 2>/dev/null; then
                        echo 'export $telemetryVar=false' >> $profilePath
                    fi
                """.trimIndent()
                
                mainHandler.post {
                    if (terminalSession != null && !sessionFinished) {
                        terminalSession!!.write(setupCommand)
                        terminalSession!!.write("\n")
                        Logger.logInfo(LOG_TAG, "Setting up GOOGLE_API_KEY")
                    }
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error setting up Google API Key", e)
            }
        }.start()
    }
    
    fun dispatchCommand(command: String) {
        if (terminalSession == null || sessionFinished) {
            Logger.logWarn(LOG_TAG, "Cannot dispatch command: terminalSession=${terminalSession != null}, sessionFinished=$sessionFinished")
            return
        }
        
        if (command.isBlank()) {
            Logger.logWarn(LOG_TAG, "Cannot dispatch empty command")
            return
        }
        
        try {
            currentTaskCommand = command
            taskStartTime = System.currentTimeMillis()
            updateTaskState(command, 0, true, TaskStatus.RUNNING)
            updateNotification()
            
            // Ensure ANDROID_SERIAL is set to localhost and ADB is connected before running droidrun
            // This ensures droidrun connects to localhost instead of looking for a physical device
            val droidrunCommand = """
                export ANDROID_SERIAL="127.0.0.1:5558"
                # Start ADB server if not running
                adb start-server 2>/dev/null || true
                # Kill any existing connection to avoid conflicts
                adb disconnect 127.0.0.1:5558 2>/dev/null || true
                # Connect ADB to localhost
                adb connect 127.0.0.1:5558
                # Wait for connection to establish
                sleep 1
                # Retry connection if first attempt failed
                if ! adb devices | grep -q "127.0.0.1:5558.*device"; then
                    adb connect 127.0.0.1:5558
                    sleep 1
                fi
                # Verify connection and show status
                if adb devices | grep -q "127.0.0.1:5558.*device"; then
                    echo "✓ ADB connected to localhost:5558"
                else
                    echo "⚠ ADB connection status:"
                    adb devices
                    echo "⚠ Continuing anyway - droidrun will use ANDROID_SERIAL=127.0.0.1:5558"
                fi
                # Run droidrun with ANDROID_SERIAL set
                droidrun run "$command"
            """.trimIndent()
            
            Logger.logInfo(LOG_TAG, "Writing command to terminal with ANDROID_SERIAL set to localhost")
            Logger.logInfo(LOG_TAG, "Command: $droidrunCommand")
            terminalSession!!.write(droidrunCommand)
            terminalSession!!.write("\n")
            Logger.logInfo(LOG_TAG, "Command written successfully")
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in dispatchCommand", e)
            updateStatus("Error: ${e.message}")
        }
    }
    
    fun resetSession() {
        startNewSession()
    }
    
    fun toggleLogsVisibility() {
        _uiState.update { it.copy(showLogs = !it.showLogs) }
    }
    
    private fun tearDownSession() {
        if (terminalSession != null) {
            try {
                terminalSession!!.finishIfRunning()
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to finish existing terminal session", e)
            }
        }
        if (currentSession != null && !sessionFinished) {
            currentSession!!.finish()
        }
        terminalSession = null
        currentSession = null
        sessionFinished = false
        mainHandler.post {
            resetSessionState()
            clearOutput()
        }
    }
    
    private fun updateTaskProgress(output: String) {
        if (currentTaskCommand == null) return
        
        val outputLower = output.lowercase()
        var shouldUpdate = false
        
        val completionPatterns = listOf(
            "goal achieved", "goal succeeded", "task completed",
            "successfully completed", "task marked as complete",
            "complete(success=true", "complete(success = true",
            "code execution successful"
        )
        
        val failurePatterns = listOf(
            "goal failed", "task failed",
            "complete(success=false", "complete(success = false"
        )
        
        val isCompleted = completionPatterns.any { outputLower.contains(it) }
        val isFailed = failurePatterns.any { outputLower.contains(it) }
        
        val lastLines = output.lines().takeLast(3).joinToString("\n").lowercase()
        val promptReturned = lastLines.matches(Regex(".*[\\$>]#\\s*$")) ||
                (lastLines.contains("$") && !lastLines.contains("droidrun run"))
        
        when {
            isCompleted -> {
                Logger.logInfo(LOG_TAG, "Task completed detected: $currentTaskCommand")
                currentTaskCommand = null
                updateTaskState(null, 0, false, TaskStatus.SUCCESS)
                shouldUpdate = true
            }
            isFailed -> {
                Logger.logInfo(LOG_TAG, "Task failed detected: $currentTaskCommand")
                currentTaskCommand = null
                updateTaskState(null, 0, false, TaskStatus.ERROR)
                shouldUpdate = true
            }
            promptReturned && !outputLower.contains("droidrun run") -> {
                val elapsed = System.currentTimeMillis() - taskStartTime
                if (elapsed > 2000) {
                    Logger.logInfo(LOG_TAG, "Command prompt returned, marking task as complete: $currentTaskCommand")
                    currentTaskCommand = null
                    updateTaskState(null, 0, false, TaskStatus.SUCCESS)
                    shouldUpdate = true
                }
            }
            else -> {
                val currentState = _uiState.value
                if (!currentState.isTaskRunning || currentState.currentTask != currentTaskCommand) {
                    updateTaskState(currentTaskCommand, 0, true, TaskStatus.RUNNING)
                    shouldUpdate = true
                }
            }
        }
        
        if (shouldUpdate) {
            updateNotification()
        }
    }
    
    private fun updateNotification() {
        val uiState = _uiState.value
        TermuxService.setTaskExecutorState(
            uiState.currentTask,
            uiState.taskProgress,
            uiState.isTaskRunning
        )
    }
    
    fun updateStatus(status: String) {
        _uiState.update { it.copy(statusText = status) }
    }
    
    fun updateOutput(output: String) {
        _uiState.update { it.copy(outputText = output) }
        if (output.isNotEmpty()) {
            updateTaskProgress(output)
        }
    }
    
    fun setUiEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isUiEnabled = enabled) }
    }
    
    fun setSessionFinished(finished: Boolean, exitCode: Int? = null) {
        val status = if (finished) {
            if (exitCode == 0) TaskStatus.SUCCESS else TaskStatus.ERROR
        } else {
            TaskStatus.STOPPED
        }
        _uiState.update {
            it.copy(
                sessionFinished = finished,
                exitCode = exitCode,
                isUiEnabled = !finished,
                currentTask = null,
                taskProgress = 0,
                isTaskRunning = false,
                taskStatus = status
            )
        }
    }
    
    fun clearOutput() {
        _uiState.update { it.copy(outputText = "") }
    }
    
    fun resetSessionState() {
        _uiState.update {
            it.copy(
                sessionFinished = false,
                exitCode = null,
                outputText = "",
                currentTask = null,
                taskProgress = 0,
                isTaskRunning = false,
                taskStatus = TaskStatus.STOPPED
            )
        }
    }
    
    fun updateTaskState(task: String?, progress: Int, isRunning: Boolean, status: TaskStatus = TaskStatus.STOPPED) {
        _uiState.update {
            it.copy(
                currentTask = task,
                taskProgress = progress,
                isTaskRunning = isRunning,
                taskStatus = status
            )
        }
    }
    
    private inner class TaskExecutorComposableSessionClient(
        private val onTranscriptUpdate: (String) -> Unit
    ) : TermuxTerminalSessionClientBase() {
        
        override fun onTextChanged(changedSession: TerminalSession) {
            refreshTranscript(changedSession)
        }
        
        override fun onSessionFinished(finishedSession: TerminalSession) {
            if (currentSession != null && !sessionFinished) {
                currentSession!!.finish()
            }
        }
        
        fun refreshTranscript(session: TerminalSession) {
            val transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, false)
            mainHandler.post {
                onTranscriptUpdate(transcript ?: "")
            }
        }
    }
}

/**
 * Task Status Component
 * Displays task status with dots and labels
 */
@Composable
fun TaskStatusComponent(
    currentStatus: TaskStatus,
    modifier: Modifier = Modifier
) {
    val buttonColor = MaterialTheme.colorScheme.primary
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = MaterialTheme.shapes.small)
            .background(color = buttonColor.copy(alpha = 0.23f))
            .border(
                width = 0.4.dp,
                color = buttonColor.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stopped
        TaskStatusItem(
            label = "Stopped",
            isActive = currentStatus == TaskStatus.STOPPED,
            color = buttonColor
        )
        
        // Running
        TaskStatusItem(
            label = "Running",
            isActive = currentStatus == TaskStatus.RUNNING,
            color = buttonColor
        )
        
        // Success
        TaskStatusItem(
            label = "Success",
            isActive = currentStatus == TaskStatus.SUCCESS,
            color = buttonColor
        )
        
        // Error
        TaskStatusItem(
            label = "Error",
            isActive = currentStatus == TaskStatus.ERROR,
            color = buttonColor
        )
    }
}

@Composable
private fun TaskStatusItem(
    label: String,
    isActive: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(shape = CircleShape)
                .background(
                    color = if (isActive) color else color.copy(alpha = 0.3f)
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) color else color.copy(alpha = 0.6f)
        )
    }
}

/**
 * Task State Dot Component
 * Shows a dot that indicates task status by color:
 * - Black = idle (STOPPED)
 * - Red = error (ERROR)
 * - Blinking green = running (RUNNING)
 * - Green = success (SUCCESS)
 */
@Composable
private fun TaskStateDot(
    status: TaskStatus,
    modifier: Modifier = Modifier
) {
    val dotColor = when (status) {
        TaskStatus.STOPPED -> Color.Black
        TaskStatus.ERROR -> Color.Red
        TaskStatus.RUNNING -> Color.Green
        TaskStatus.SUCCESS -> Color.Green
    }
    
    // Blinking animation for running state
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink_alpha"
    )
    
    val animatedColor = if (status == TaskStatus.RUNNING) {
        dotColor.copy(alpha = alpha)
    } else {
        dotColor
    }
    
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(shape = CircleShape)
            .background(color = animatedColor)
    )
}

/**
 * Reusable Task Executor Composable
 * Can be placed in any Activity
 */
@Composable
fun TaskExecutorComposable(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    
    val viewModel: TaskExecutorComposableViewModel = remember(activity) {
        val factory = TaskExecutorComposableViewModelFactory(activity)
        ViewModelProvider(activity as ViewModelStoreOwner, factory)[TaskExecutorComposableViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    var commandText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val logsScrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Check and request audio permission
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
    
    // Speech recognition utility
    val speechRecognitionUtil = remember {
        SpeechRecognitionUtil(
            context = context,
            onResult = { text ->
                commandText = text
                com.termux.shared.logger.Logger.logInfo("TaskExecutor", "Speech result: $text")
            },
            onError = { error ->
                isListening = false
                com.termux.shared.logger.Logger.logError("TaskExecutor", "Speech recognition error: $error")
            }
        )
    }
    
    // Cleanup speech recognition on dispose
    DisposableEffect(Unit) {
        onDispose {
            speechRecognitionUtil.destroy()
        }
    }
    
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
    
    // Auto-scroll to bottom when output changes
    LaunchedEffect(uiState.outputText) {
        if (uiState.showLogs && uiState.outputText.isNotEmpty()) {
            kotlinx.coroutines.delay(50) // Small delay to ensure text is rendered
            logsScrollState.animateScrollTo(logsScrollState.maxValue)
        }
    }
    
    fun toggleVoiceInput() {
        if (isListening) {
            speechRecognitionUtil.stopListening()
            isListening = false
            com.termux.shared.logger.Logger.logInfo("TaskExecutor", "Stopped listening")
        } else {
            // Check permission first
            if (!hasAudioPermission) {
                com.termux.shared.logger.Logger.logInfo("TaskExecutor", "Requesting audio permission")
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            
            if (speechRecognitionUtil.isAvailable()) {
                com.termux.shared.logger.Logger.logInfo("TaskExecutor", "Starting speech recognition")
                speechRecognitionUtil.startListening()
                isListening = true
            } else {
                com.termux.shared.logger.Logger.logError("TaskExecutor", "Speech recognition not available")
            }
        }
    }
    
    val buttonColor = MaterialTheme.colorScheme.primary
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Task status component with eye icon button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskStatusComponent(
                currentStatus = uiState.taskStatus,
                modifier = Modifier.weight(1f)
            )
            
            // Small eye icon button
            IconButton(
                onClick = { viewModel.toggleLogsVisibility() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = if (uiState.showLogs) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                    contentDescription = if (uiState.showLogs) "Hide Logs" else "Show Logs",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Logs area (conditionally visible) - styled like FavoriteItem
        if (uiState.showLogs) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(shape = MaterialTheme.shapes.small)
                    .background(color = buttonColor.copy(alpha = 0.23f))
                    .border(
                        width = 0.4.dp,
                        color = buttonColor.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
            ) {
                if (uiState.outputText.isEmpty()) {
                    // Show placeholder when no logs
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No logs yet. Run a task to see output here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = buttonColor.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    // Show logs with proper scrolling
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(logsScrollState)
                            .padding(16.dp)
                    ) {
                        // Split output into lines for better rendering
                        val lines = uiState.outputText.lines()
                        lines.forEach { line ->
                            Text(
                                text = line.ifEmpty { " " }, // Empty lines show as space
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = buttonColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        
        // Command input with state dot and action icons
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = commandText,
            onValueChange = { commandText = it },
            placeholder = {
                Text(text = "Enter Task")
            },
            shape = MaterialTheme.shapes.small,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                imeAction = ImeAction.Done
            ),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            leadingIcon = {
                // State indicator dot
                TaskStateDot(status = uiState.taskStatus)
            },
            trailingIcon = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset session icon
                    IconButton(
                        onClick = { viewModel.resetSession() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Reset Session",
                            tint = buttonColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Execute icon
                    IconButton(
                        onClick = {
                            if (commandText.isNotBlank()) {
                                viewModel.dispatchCommand(commandText)
                                commandText = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = "Execute",
                            tint = buttonColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )
        
        // Mic button - centered horizontally
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
        ) {
            IconButton(
                onClick = { toggleVoiceInput() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mic),
                    contentDescription = if (isListening) "Stop listening" else "Start voice input",
                    tint = if (isListening) MaterialTheme.colorScheme.error else buttonColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

