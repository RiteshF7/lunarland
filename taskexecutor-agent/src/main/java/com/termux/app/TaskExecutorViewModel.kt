package com.termux.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.termux.app.taskexecutor.model.DroidRunConfig
import com.termux.app.taskexecutor.model.TaskExecutorUiState
import com.termux.app.taskexecutor.model.TaskStatus
import com.termux.app.taskexecutor.state.TaskExecutorStateManager
import com.termux.shared.logger.Logger
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession.TermuxSessionClient
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import com.termux.app.TermuxService
import com.termux.app.TermuxInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

class TaskExecutorViewModelFactory(
    private val activity: Activity,
    private val googleApiKey: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TaskExecutorViewModel(activity, googleApiKey) as T
    }
}

class TaskExecutorViewModel(
    private val activity: Activity,
    private val googleApiKey: String = ""
) : AndroidViewModel(activity.application) {
    
    private val LOG_TAG = "TaskExecutorViewModel"
    
    private val _uiState = MutableStateFlow(TaskExecutorUiState())
    val uiState: StateFlow<TaskExecutorUiState> = _uiState.asStateFlow()
    
    private val stateManager = TaskExecutorStateManager()
    private val sessionIds = AtomicInteger()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    var termuxService: TermuxService? = null
    var currentSession: TermuxSession? = null
    var terminalSession: TerminalSession? = null
    private var sessionClient: TaskExecutorSessionClient? = null
    var sessionFinished = false
    var isServiceBound = false
    var currentTaskCommand: String? = null
    var taskStartTime: Long = 0
    
    // Track droidrun preparation state
    private var isDroidrunPrepared = false
    
    /**
     * DroidRun configuration with all CLI flags and options.
     * Easily configurable - modify this object to change droidrun behavior.
     * 
     * Example usage:
     * - viewModel.droidRunConfig = DroidRunConfig.DEFAULT.copy(steps = 200, reasoning = true)
     * - viewModel.droidRunConfig = DroidRunConfig.HIGH_PERFORMANCE
     * - viewModel.droidRunConfig = DroidRunConfig.DIRECT_MODE
     * - viewModel.droidRunConfig = DroidRunConfig.DEBUG
     */
    var droidRunConfig: DroidRunConfig = DroidRunConfig.DEFAULT
        set(value) {
            field = value
            Logger.logInfo(LOG_TAG, "DroidRun config updated: steps=${value.steps}, reasoning=${value.reasoning}, vision=${value.vision}, debug=${value.debug}")
        }
    
    // Legacy properties for backward compatibility (delegate to droidRunConfig)
    @Deprecated("Use droidRunConfig.steps instead", ReplaceWith("droidRunConfig.steps"))
    var maxDroidrunSteps: Int
        get() = droidRunConfig.steps
        set(value) {
            droidRunConfig = droidRunConfig.copy(steps = value)
        }
    
    @Deprecated("Use droidRunConfig.reasoning instead", ReplaceWith("droidRunConfig.reasoning"))
    var enableReasoning: Boolean
        get() = droidRunConfig.reasoning
        set(value) {
            droidRunConfig = droidRunConfig.copy(reasoning = value)
        }
    
    // Track previous output length to detect if output is still changing
    private var previousOutputLength: Int = 0
    private var lastOutputChangeTime: Long = 0
    
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
        // Clean up session when unbinding service
        tearDownSession()
    }
    
    override fun onCleared() {
        super.onCleared()
        Logger.logInfo(LOG_TAG, "ViewModel cleared, disposing session")
        tearDownSession()
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
        
        // Check if we can reuse existing session
        if (isSessionValid()) {
            Logger.logInfo(LOG_TAG, "Reusing existing valid session")
            mainHandler.post {
                setUiEnabled(true)
                updateTaskState(null, 0, false, TaskStatus.STOPPED)
                updateStatus("Ready")
            }
            // Ensure droidrun is prepared
            if (!isDroidrunPrepared) {
                prepareDroidrunEnvironment()
            }
            return
        }
        
        // Tear down invalid session before creating new one
        tearDownSession()
        val client = TaskExecutorSessionClient { transcript ->
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
                    val shellManagerField = termuxService!!.javaClass.getDeclaredField("mShellManager")
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
            updateStatus("Ready") // Clear the "Setting up environment..." message
            Logger.logInfo(LOG_TAG, "Session ready, UI enabled")
        }
        client.refreshTranscript(terminalSession!!)
        
        // Don't run ADB setup on startup - it interferes with host ADB connections
        // ADB setup will be done lazily when actually needed for droidrun
        // setupAdbEnvironment()  // Disabled to prevent breaking host ADB connections
        setupGoogleApiKey()
        
        // Prepare droidrun environment only when actually needed (lazy initialization)
        // Don't prepare on startup to avoid interfering with host ADB connections
        // if (!isDroidrunPrepared) {
        //     prepareDroidrunEnvironment()
        // }
    }
    
    /**
     * Check if current session is valid and can be reused
     */
    private fun isSessionValid(): Boolean {
        return currentSession != null &&
               terminalSession != null &&
               !sessionFinished &&
               terminalSession!!.isRunning() &&
               termuxService != null
    }
    
    // Removed setupAdbEnvironment() - droidrun handles ADB connection automatically
    // No manual ADB setup needed as it interferes with host ADB connections
    
    private fun setupGoogleApiKey() {
        if (terminalSession == null || sessionFinished) return
        
        Thread {
            try {
                val apiKeyFromProps = googleApiKey
                
                Logger.logInfo(LOG_TAG, "setupGoogleApiKey called, apiKey length: ${apiKeyFromProps.length}, isBlank: ${apiKeyFromProps.isBlank()}")
                
                if (apiKeyFromProps.isBlank()) {
                    mainHandler.post {
                        updateStatus("GOOGLE_API_KEY not found in BuildConfig")
                        Logger.logError(LOG_TAG, "GOOGLE_API_KEY not found in BuildConfig (passed from Activity)")
                    }
                    return@Thread
                }
                
                Logger.logInfo(LOG_TAG, "Setting up GOOGLE_API_KEY in environment (length: ${apiKeyFromProps.length})")
                
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
    
    /**
     * Prepare droidrun environment - simplified.
     * Droidrun has built-in ADB connection, so we just mark it as prepared.
     */
    private fun prepareDroidrunEnvironment() {
        if (isDroidrunPrepared) {
            return
        }
        // Droidrun handles ADB connection automatically, no setup needed
        isDroidrunPrepared = true
        Logger.logInfo(LOG_TAG, "Droidrun environment ready (droidrun will handle ADB connection)")
    }
    
    fun dispatchCommand(command: String) {
        Logger.logInfo(LOG_TAG, "dispatchCommand called with: '$command'")
        Logger.logInfo(LOG_TAG, "terminalSession=${terminalSession != null}, sessionFinished=$sessionFinished, taskStatus=${_uiState.value.taskStatus}")
        
        if (terminalSession == null || sessionFinished) {
            Logger.logWarn(LOG_TAG, "Cannot dispatch command: terminalSession=${terminalSession != null}, sessionFinished=$sessionFinished")
            updateStatus("Session not ready. Please wait...")
            return
        }
        
        // Prevent new tasks if one is already running
        if (_uiState.value.taskStatus == TaskStatus.RUNNING) {
            Logger.logWarn(LOG_TAG, "Cannot dispatch command: task is already running")
            updateStatus("Task is already running. Please stop it first.")
            return
        }
        
        if (command.isBlank()) {
            Logger.logWarn(LOG_TAG, "Cannot dispatch empty command")
            return
        }
        
        try {
            Logger.logInfo(LOG_TAG, "Dispatching command: $command")
            
            // Use state manager to transition to running state
            // State manager will handle transition from SUCCESS/ERROR/IDLE to RUNNING
            val stateResult = stateManager.transitionToRunning(command)
            when (stateResult) {
                is TaskExecutorStateManager.StateTransitionResult.Success -> {
                    currentTaskCommand = command
                    taskStartTime = System.currentTimeMillis()
                    previousOutputLength = 0
                    lastOutputChangeTime = System.currentTimeMillis()
                    updateTaskState(
                        task = command,
                        progress = 0,
                        isRunning = stateResult.isRunning,
                        status = stateResult.status
                    )
                    _uiState.update { 
                        it.copy(
                            agentStateMessage = stateResult.message,
                            maxSteps = droidRunConfig.steps
                        )
                    }
                    updateStatus(stateResult.message)
                    updateNotification()
                }
                is TaskExecutorStateManager.StateTransitionResult.Error -> {
                    Logger.logWarn(LOG_TAG, "State transition failed: ${stateResult.message}")
                    updateStatus(stateResult.message)
                    return
                }
            }
            
            // Mark droidrun as prepared (it handles ADB connection automatically)
            if (!isDroidrunPrepared) {
                prepareDroidrunEnvironment()
            }
            
            // Simple command: droidrun handles ADB connection automatically via ANDROID_SERIAL
            val apiKeyExport = if (googleApiKey.isNotBlank()) {
                "export GOOGLE_API_KEY=\"$googleApiKey\" && "
            } else {
                ""
            }
            
            // Build droidrun command with all configured flags
            val droidrunFlags = droidRunConfig.buildFlagsString()
            val deviceSerial = droidRunConfig.device ?: "127.0.0.1:5558"
            
            // Droidrun will automatically connect via ANDROID_SERIAL - no manual ADB commands needed
            val droidrunCommand = """
                export ANDROID_SERIAL="$deviceSerial"
                ${apiKeyExport}droidrun run $droidrunFlags "$command"
            """.trimIndent()
            
            Logger.logInfo(LOG_TAG, "DroidRun command flags: $droidrunFlags")
            Logger.logInfo(LOG_TAG, "DroidRun config: steps=${droidRunConfig.steps}, reasoning=${droidRunConfig.reasoning}, vision=${droidRunConfig.vision}, debug=${droidRunConfig.debug}")
            
            Logger.logInfo(LOG_TAG, "Writing simplified droidrun command to terminal")
            terminalSession!!.write(droidrunCommand)
            terminalSession!!.write("\n")
            Logger.logInfo(LOG_TAG, "Command written successfully to terminal session")
            
            // Verify the command was written
            Logger.logInfo(LOG_TAG, "Terminal session state: isRunning=${terminalSession!!.isRunning()}")
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in dispatchCommand", e)
            val errorResult = stateManager.transitionToError()
            if (errorResult is TaskExecutorStateManager.StateTransitionResult.Success) {
                updateTaskState(null, 0, false, errorResult.status)
                _uiState.update { it.copy(agentStateMessage = errorResult.message) }
                updateStatus(errorResult.message)
            } else {
                updateStatus("Error: ${e.message}")
            }
        }
    }
    
    fun resetSession() {
        startNewSession()
    }
    
    fun toggleLogsVisibility() {
        _uiState.update { it.copy(showLogs = !it.showLogs) }
    }
    
    fun stopCurrentTask() {
        if (terminalSession != null && !sessionFinished && (stateManager.isTaskRunning() || stateManager.isTaskPaused())) {
            try {
                val task = stateManager.getCurrentTask()
                Logger.logInfo(LOG_TAG, "Stopping task: $task")
                
                // Send Ctrl+C multiple times to ensure the command is interrupted
                // This is important for droidrun which may be in a subprocess
                terminalSession!!.write("\u0003") // First Ctrl+C
                Thread.sleep(100)
                terminalSession!!.write("\u0003") // Second Ctrl+C (in case first didn't work)
                Thread.sleep(100)
                
                // Also try sending Enter to confirm cancellation if needed
                terminalSession!!.write("\n")
                
                Logger.logInfo(LOG_TAG, "Sent stop signals to terminal")
                
                // Update state through state manager
                val stopResult = stateManager.forceStop()
                if (stopResult is TaskExecutorStateManager.StateTransitionResult.Success) {
                    currentTaskCommand = null
                    updateTaskState(null, 0, false, stopResult.status)
                    _uiState.update { it.copy(agentStateMessage = stopResult.message) }
                    updateStatus(stopResult.message)
                    updateNotification()
                }
                
                // Clear output after a brief delay to show the stop message
                Thread {
                    Thread.sleep(500)
                    mainHandler.post {
                        if (_uiState.value.taskStatus == TaskStatus.STOPPED) {
                            updateOutput("Task stopped by user\n")
                        }
                    }
                }.start()
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error stopping task", e)
                updateStatus("Error stopping task: ${e.message}")
            }
        } else {
            Logger.logWarn(LOG_TAG, "Cannot stop task: terminalSession=${terminalSession != null}, sessionFinished=$sessionFinished, isTaskRunning=${stateManager.isTaskRunning()}, isTaskPaused=${stateManager.isTaskPaused()}")
        }
    }
    
    fun pauseCurrentTask() {
        if (terminalSession != null && !sessionFinished && stateManager.isTaskRunning()) {
            try {
                val task = stateManager.getCurrentTask()
                Logger.logInfo(LOG_TAG, "Pausing task: $task")
                
                // Send Ctrl+Z to suspend the process (SIGTSTP)
                terminalSession!!.write("\u001A")
                Thread.sleep(100)
                
                Logger.logInfo(LOG_TAG, "Sent pause signal (Ctrl+Z) to terminal")
                
                // Update state through state manager
                val pauseResult = stateManager.transitionToPaused()
                if (pauseResult is TaskExecutorStateManager.StateTransitionResult.Success) {
                    updateTaskState(
                        task = stateManager.getCurrentTask(),
                        progress = _uiState.value.taskProgress,
                        isRunning = pauseResult.isRunning,
                        status = pauseResult.status
                    )
                    _uiState.update { it.copy(agentStateMessage = pauseResult.message) }
                    updateStatus(pauseResult.message)
                    updateNotification()
                } else if (pauseResult is TaskExecutorStateManager.StateTransitionResult.Error) {
                    Logger.logWarn(LOG_TAG, "Failed to pause task: ${pauseResult.message}")
                    updateStatus("Failed to pause: ${pauseResult.message}")
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error pausing task", e)
                updateStatus("Error pausing task: ${e.message}")
            }
        } else {
            Logger.logWarn(LOG_TAG, "Cannot pause task: terminalSession=${terminalSession != null}, sessionFinished=$sessionFinished, isTaskRunning=${stateManager.isTaskRunning()}")
        }
    }
    
    /**
     * Toggle pause/resume - if paused, resume; if running, pause
     */
    fun togglePauseResume() {
        when {
            stateManager.isTaskPaused() -> resumeCurrentTask()
            stateManager.isTaskRunning() -> pauseCurrentTask()
            else -> Logger.logWarn(LOG_TAG, "Cannot toggle pause/resume: task is not running or paused")
        }
    }
    
    fun resumeCurrentTask() {
        if (terminalSession != null && !sessionFinished && stateManager.isTaskPaused()) {
            try {
                val task = stateManager.getCurrentTask()
                Logger.logInfo(LOG_TAG, "Resuming task: $task")
                
                // Send 'fg' command to bring the paused process back to foreground
                terminalSession!!.write("fg\n")
                Thread.sleep(100)
                
                Logger.logInfo(LOG_TAG, "Sent resume command (fg) to terminal")
                
                // Update state through state manager
                val resumeResult = stateManager.transitionToResume()
                if (resumeResult is TaskExecutorStateManager.StateTransitionResult.Success) {
                    updateTaskState(
                        task = stateManager.getCurrentTask(),
                        progress = _uiState.value.taskProgress,
                        isRunning = resumeResult.isRunning,
                        status = resumeResult.status
                    )
                    _uiState.update { it.copy(agentStateMessage = resumeResult.message) }
                    updateStatus(resumeResult.message)
                    updateNotification()
                } else if (resumeResult is TaskExecutorStateManager.StateTransitionResult.Error) {
                    Logger.logWarn(LOG_TAG, "Failed to resume task: ${resumeResult.message}")
                    updateStatus("Failed to resume: ${resumeResult.message}")
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error resuming task", e)
                updateStatus("Error resuming task: ${e.message}")
            }
        } else {
            Logger.logWarn(LOG_TAG, "Cannot resume task: terminalSession=${terminalSession != null}, sessionFinished=$sessionFinished, isTaskPaused=${stateManager.isTaskPaused()}")
        }
    }
    
    private fun tearDownSession() {
        Logger.logInfo(LOG_TAG, "Tearing down session")
        
        // Remove session from service's session list if it exists
        if (currentSession != null && termuxService != null) {
            try {
                val shellManagerField = termuxService!!.javaClass.getDeclaredField("mShellManager")
                shellManagerField.isAccessible = true
                val shellManager = shellManagerField.get(termuxService)
                val sessionsField = shellManager.javaClass.getDeclaredField("mTermuxSessions")
                sessionsField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val sessions = sessionsField.get(shellManager) as MutableList<Any>
                val sessionToRemove: Any? = currentSession
                if (sessionToRemove != null && sessions.contains(sessionToRemove)) {
                    sessions.remove(sessionToRemove)
                    Logger.logInfo(LOG_TAG, "Removed session from service manager")
                }
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to remove session from service manager: ${e.message}")
            }
        }
        
        if (terminalSession != null) {
            try {
                terminalSession!!.finishIfRunning()
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to finish existing terminal session", e)
            }
        }
        if (currentSession != null && !sessionFinished) {
            try {
                currentSession!!.finish()
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to finish session", e)
            }
        }
        terminalSession = null
        currentSession = null
        sessionClient = null
        sessionFinished = false
        isDroidrunPrepared = false // Reset droidrun preparation state
        mainHandler.post {
            resetSessionState()
            clearOutput()
        }
    }
    
    private fun updateTaskProgress(output: String) {
        // Only process if we have a running task
        if (!stateManager.isTaskRunning()) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val outputLower = output.lowercase()
        val elapsed = currentTime - taskStartTime
        
        // Track if output has changed - update timestamp when output length changes
        val outputLength = output.length
        if (outputLength != previousOutputLength) {
            lastOutputChangeTime = currentTime
            previousOutputLength = outputLength
        }
        
        Logger.logDebug(LOG_TAG, "updateTaskProgress: checking output for task: ${stateManager.getCurrentTask()}, elapsed: ${elapsed}ms")
        
        // Only use explicit completion patterns from droidrun - be very strict
        val completionPatterns = listOf(
            "goal achieved", 
            "goal succeeded", 
            "task completed successfully",
            "successfully completed",
            "task marked as complete",
            "complete(success=true",
            "complete(success = true",
            "code execution successful",
            "task execution completed"
        )
        
        val failurePatterns = listOf(
            "goal failed", 
            "task failed",
            "complete(success=false", 
            "complete(success = false",
            "execution failed"
        )
        
        // Check for explicit completion/failure patterns in the LAST LINES of output only
        // This is more reliable than checking the entire output
        val lastLines = output.lines().takeLast(20).joinToString("\n")
        val lastLinesLower = lastLines.lowercase()
        
        val isCompleted = completionPatterns.any { pattern -> 
            lastLinesLower.contains(pattern) && elapsed > 3000 // Must have run for at least 3 seconds
        }
        val isFailed = failurePatterns.any { pattern -> 
            lastLinesLower.contains(pattern) && elapsed > 3000
        }
        
        // Check if terminal session is still running - this is the most reliable indicator
        val isTerminalRunning = terminalSession != null && terminalSession!!.isRunning() && !sessionFinished
        
        // Check if output has stabilized (no new output for a period) - indicates task might be done
        val timeSinceLastOutputChange = if (lastOutputChangeTime > 0) currentTime - lastOutputChangeTime else Long.MAX_VALUE
        val outputStable = timeSinceLastOutputChange > 5000 // 5 seconds with no output changes
        
        // Check if droidrun-related activity indicators are present
        val hasRecentDroidrunActivity = lastLinesLower.contains("droidrun") || 
                                       lastLinesLower.contains("step") ||
                                       lastLinesLower.contains("executing") ||
                                       lastLinesLower.contains("processing") ||
                                       lastLinesLower.contains("thinking") ||
                                       lastLinesLower.contains("action")
        
        when {
            // Explicit completion pattern found
            isCompleted -> {
                // Only transition to SUCCESS if:
                // 1. Terminal session is NOT running (definitive sign it's done), OR
                // 2. Output has been stable for 5+ seconds AND no recent droidrun activity AND at least 5 seconds elapsed
                val canComplete = if (!isTerminalRunning) {
                    // Terminal stopped - definitely done
                    Logger.logInfo(LOG_TAG, "Terminal session stopped, task is complete")
                    true
                } else if (outputStable && !hasRecentDroidrunActivity && elapsed > 5000) {
                    // Output stabilized with no activity - likely done
                    Logger.logInfo(LOG_TAG, "Output stabilized with completion pattern, task appears complete")
                    true
                } else {
                    // Still running or recently active - keep running state
                    Logger.logDebug(LOG_TAG, "Completion pattern found but task still active (terminalRunning=$isTerminalRunning, stable=$outputStable, hasActivity=$hasRecentDroidrunActivity), keeping RUNNING state")
                    // Ensure state remains RUNNING
                    val currentState = _uiState.value
                    if (currentState.taskStatus != TaskStatus.RUNNING || !currentState.isTaskRunning) {
                        val runningResult = stateManager.transitionToRunning(stateManager.getCurrentTask() ?: "")
                        if (runningResult is TaskExecutorStateManager.StateTransitionResult.Success) {
                            updateTaskState(
                                task = stateManager.getCurrentTask(),
                                progress = 0,
                                isRunning = runningResult.isRunning,
                                status = runningResult.status
                            )
                            _uiState.update { it.copy(agentStateMessage = runningResult.message) }
                            updateNotification()
                        }
                    }
                    false
                }
                
                if (canComplete) {
                    Logger.logInfo(LOG_TAG, "Task completed detected: ${stateManager.getCurrentTask()}")
                    val result = stateManager.transitionToSuccess()
                    if (result is TaskExecutorStateManager.StateTransitionResult.Success) {
                        currentTaskCommand = null
                        updateTaskState(null, 0, false, result.status)
                        _uiState.update { it.copy(agentStateMessage = result.message) }
                        updateStatus(result.message)
                        updateNotification()
                    }
                }
            }
            // Explicit failure pattern found
            isFailed -> {
                // Similar logic for failures - only mark as failed if terminal stopped or output stabilized
                val canFail = !isTerminalRunning || (outputStable && !hasRecentDroidrunActivity && elapsed > 5000)
                
                if (canFail) {
                    Logger.logInfo(LOG_TAG, "Task failed detected: ${stateManager.getCurrentTask()}")
                    val result = stateManager.transitionToError()
                    if (result is TaskExecutorStateManager.StateTransitionResult.Success) {
                        currentTaskCommand = null
                        updateTaskState(null, 0, false, result.status)
                        _uiState.update { it.copy(agentStateMessage = result.message) }
                        updateStatus(result.message)
                        updateNotification()
                    }
                } else {
                    Logger.logDebug(LOG_TAG, "Failure pattern found but task still active, keeping RUNNING state")
                }
            }
            // Task is still running - ALWAYS keep RUNNING state
            else -> {
                // Always ensure state is RUNNING if we're here
                // This is critical to prevent premature state changes
                val currentState = _uiState.value
                if (currentState.taskStatus != TaskStatus.RUNNING || !currentState.isTaskRunning) {
                    Logger.logDebug(LOG_TAG, "Ensuring state is RUNNING (task still active, terminalRunning=$isTerminalRunning)")
                    val runningResult = stateManager.transitionToRunning(stateManager.getCurrentTask() ?: "")
                    if (runningResult is TaskExecutorStateManager.StateTransitionResult.Success) {
                        updateTaskState(
                            task = stateManager.getCurrentTask(),
                            progress = 0,
                            isRunning = runningResult.isRunning,
                            status = runningResult.status
                        )
                        _uiState.update { it.copy(agentStateMessage = runningResult.message) }
                        updateNotification()
                    }
                }
            }
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
        // Reset state manager
        val idleResult = stateManager.transitionToIdle()
        val message = if (idleResult is TaskExecutorStateManager.StateTransitionResult.Success) {
            idleResult.message
        } else {
            "Ready"
        }
        
        _uiState.update {
            it.copy(
                sessionFinished = false,
                exitCode = null,
                outputText = "",
                statusText = message,
                currentTask = null,
                taskProgress = 0,
                isTaskRunning = false,
                taskStatus = TaskStatus.STOPPED,
                agentStateMessage = message
            )
        }
        currentTaskCommand = null
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
    
    private inner class TaskExecutorSessionClient(
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

