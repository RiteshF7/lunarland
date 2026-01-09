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
import com.termux.app.TaskExecutorOverlayService
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
        
        // Set up overlay service callbacks
        setupOverlayCallbacks()
        
        setupAdbEnvironment()
        setupGoogleApiKey()
        
        // Prepare droidrun environment once per app lifecycle
        if (!isDroidrunPrepared) {
            prepareDroidrunEnvironment()
        }
    }
    
    /**
     * Set up callbacks for overlay service
     */
    private fun setupOverlayCallbacks() {
        TaskExecutorOverlayService.setStopCallback {
            stopCurrentTask()
        }
        TaskExecutorOverlayService.setPauseCallback {
            togglePauseResume()
        }
        Logger.logInfo(LOG_TAG, "Overlay service callbacks set up")
    }
    
    /**
     * Start the overlay service to show task logs and controls
     */
    private fun startOverlayService() {
        try {
            val intent = Intent(activity, TaskExecutorOverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity.startForegroundService(intent)
            } else {
                activity.startService(intent)
            }
            Logger.logInfo(LOG_TAG, "Started overlay service")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to start overlay service: ${e.message}")
            Logger.logStackTraceWithMessage(LOG_TAG, "Error starting overlay", e)
        }
    }
    
    /**
     * Stop the overlay service
     */
    private fun stopOverlayService() {
        try {
            val intent = Intent(activity, TaskExecutorOverlayService::class.java)
            activity.stopService(intent)
            Logger.logInfo(LOG_TAG, "Stopped overlay service")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to stop overlay service: ${e.message}")
        }
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
    
    private fun setupAdbEnvironment() {
        if (terminalSession == null || sessionFinished) return
        
        Thread {
            try {
                val adbSetupCommand = """
                    export TMPDIR="${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"
                    mkdir -p "${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"
                    # Ensure system ADB is in PATH (use system binaries)
                    export PATH="/system/bin:/system/xbin:${'$'}PATH"
                    # Check if ADB is available
                    ADB_CMD=""
                    if command -v adb >/dev/null 2>&1; then
                        ADB_CMD="adb"
                    elif [ -f /data/data/com.termux/files/usr/bin/adb ]; then
                        ADB_CMD="/data/data/com.termux/files/usr/bin/adb"
                    else
                        echo "⚠ ADB not found in Termux!"
                        echo "⚠ Please install ADB by running: pkg install android-tools"
                        echo "⚠ After installation, restart Termux or run: source ~/.bashrc"
                        echo ""
                        echo "Skipping ADB setup - ADB not installed"
                        ADB_CMD=""
                    fi
                    if [ -n "${'$'}ADB_CMD" ]; then
                        echo "Using ADB: ${'$'}ADB_CMD"
                        # Start ADB server if not running
                        echo "Starting ADB server..."
                        ${'$'}ADB_CMD start-server 2>&1 || true
                        sleep 2
                        # Get device serial from system properties
                        DEVICE_SERIAL=$(getprop ro.serialno 2>/dev/null || echo "")
                        echo "Device serial: ${'$'}DEVICE_SERIAL"
                        # Check current devices
                        echo ""
                        echo "=== Current ADB devices ==="
                        ${'$'}ADB_CMD devices -l 2>&1
                        echo "=========================="
                        # When running ADB inside the device, it might not show the device itself
                        # We need to enable TCP/IP mode first, then connect via localhost
                        DEVICE_COUNT=$(${'$'}ADB_CMD devices 2>&1 | grep -v "List" | grep -cE "device|unauthorized|offline" || echo "0")
                        if [ "${'$'}DEVICE_COUNT" -eq 0 ]; then
                            echo ""
                            echo "No devices found in ADB list."
                            echo "This is normal when running ADB inside the device itself."
                            echo "Attempting to enable TCP/IP mode and connect..."
                            # First, try to enable TCP/IP mode (this might fail if no device is connected)
                            # We'll try port 5555 first
                            echo "Enabling TCP/IP mode on port 5555..."
                            ${'$'}ADB_CMD tcpip 5555 2>&1 || {
                                echo "⚠ Could not enable TCP/IP mode (device may need to be connected via USB first)"
                                echo "⚠ If you're connected via USB from a computer, run: adb tcpip 5555"
                            }
                            sleep 2
                            # Try connecting to localhost on port 5555
                            echo "Connecting to localhost:5555..."
                            ${'$'}ADB_CMD connect 127.0.0.1:5555 2>&1 || true
                            sleep 2
                            # Check connection status
                            CONNECTION_STATUS=$(${'$'}ADB_CMD devices 2>&1 | grep "127.0.0.1:5555" || echo "")
                            if echo "${'$'}CONNECTION_STATUS" | grep -q "device"; then
                                echo "✓ Successfully connected to device via localhost:5555"
                            elif echo "${'$'}CONNECTION_STATUS" | grep -q "unauthorized"; then
                                echo "⚠ Device connected but unauthorized. Please authorize on device screen."
                                echo "⚠ After authorization, device will appear in 'adb devices'"
                                # Try to reconnect to trigger authorization prompt
                                ${'$'}ADB_CMD reconnect 2>&1 || true
                                sleep 2
                            elif echo "${'$'}CONNECTION_STATUS" | grep -q "offline"; then
                                echo "⚠ Device is offline. Trying to reconnect..."
                                ${'$'}ADB_CMD reconnect 2>&1 || true
                                sleep 2
                            else
                                echo "⚠ Could not connect to device. TCP/IP mode may need to be enabled externally."
                                echo "⚠ To fix: Connect device via USB from computer and run: adb tcpip 5555"
                            fi
                            # Count devices again after reconnection attempt
                            DEVICE_COUNT=$(${'$'}ADB_CMD devices 2>&1 | grep -v "List" | grep -cE "device|unauthorized|offline" || echo "0")
                            # Show current device list
                            echo ""
                            echo "=== ADB devices after connection attempt ==="
                            ${'$'}ADB_CMD devices -l 2>&1
                            echo "==========================================="
                        else
                            echo ""
                            echo "✓ Found ${'$'}DEVICE_COUNT device(s) in ADB list"
                        fi
                        # Enable TCP/IP mode on port 5558 for droidrun
                        echo ""
                        echo "Setting up TCP/IP mode on port 5558 for droidrun..."
                        # Try to find any connected device
                        CONNECTED_DEVICE=$(${'$'}ADB_CMD devices 2>&1 | grep -v "List" | grep "device" | head -1 | awk '{print ${'$'}1}')
                        if [ -n "${'$'}CONNECTED_DEVICE" ]; then
                            echo "Found device: ${'$'}CONNECTED_DEVICE, enabling TCP/IP mode on port 5558..."
                            ${'$'}ADB_CMD -s ${'$'}CONNECTED_DEVICE tcpip 5558 2>&1 || {
                                echo "⚠ Failed to enable TCP/IP on port 5558, trying alternative..."
                                ${'$'}ADB_CMD tcpip 5558 2>&1 || true
                            }
                            sleep 1
                            # Connect to localhost:5558
                            ${'$'}ADB_CMD disconnect 127.0.0.1:5558 2>&1 || true
                            sleep 0.5
                            ${'$'}ADB_CMD connect 127.0.0.1:5558 2>&1 || true
                            sleep 1.5
                        else
                            echo "⚠ No device found to enable TCP/IP mode"
                        fi
                        # Final device list
                        echo ""
                        echo "=== Final ADB devices list ==="
                        ${'$'}ADB_CMD devices 2>&1
                        echo "=============================="
                    fi
                    # Setup TMPDIR in .bashrc
                    if ! grep -q "export TMPDIR" "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc" 2>/dev/null; then
                        echo 'export TMPDIR="${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"' >> "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                        mkdir -p "${TermuxConstants.TERMUX_HOME_DIR_PATH}/usr/tmp"
                    fi
                    # Ensure PATH includes system binaries in .bashrc
                    if ! grep -q "export PATH.*system" "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc" 2>/dev/null; then
                        echo 'export PATH="/system/bin:/system/xbin:${'$'}PATH"' >> "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                    fi
                    # Remove ANDROID_SERIAL from .bashrc if it exists (to allow all devices to show)
                    if grep -q "export ANDROID_SERIAL" "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc" 2>/dev/null; then
                        sed -i '/export ANDROID_SERIAL/d' "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                        echo "Removed global ANDROID_SERIAL to allow all devices to be visible"
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
     * Prepare droidrun environment once per app lifecycle.
     * Sets up ADB server, TCP/IP mode, and connection to localhost:5558.
     */
    private fun prepareDroidrunEnvironment() {
        if (terminalSession == null || sessionFinished || isDroidrunPrepared) {
            return
        }
        
        Thread {
            try {
                Logger.logInfo(LOG_TAG, "Preparing droidrun environment (one-time setup)")
                
                val droidrunSetupCommand = """
                    export ANDROID_SERIAL="127.0.0.1:5558"
                    # Kill and restart ADB server
                    echo "Preparing ADB environment for droidrun..."
                    adb kill-server 2>&1 || true
                    sleep 0.5
                    adb start-server 2>&1 || true
                    sleep 1
                    # Enable TCP/IP mode on port 5558
                    echo "Enabling ADB TCP/IP mode on port 5558..."
                    adb tcpip 5558 2>&1 || {
                        echo "⚠ Failed to enable TCP/IP mode, trying alternative method..."
                        # Try with USB device first if available
                        USB_DEVICE=$(adb devices | grep -v "List" | grep "device" | head -1 | awk '{print ${'$'}1}')
                        if [ -n "${'$'}USB_DEVICE" ]; then
                            adb -s ${'$'}USB_DEVICE tcpip 5558 2>&1 || true
                        fi
                    }
                    sleep 1
                    # Kill any existing connection to avoid conflicts
                    adb disconnect 127.0.0.1:5558 2>&1 || true
                    sleep 0.5
                    # Connect ADB to localhost with multiple retries
                    MAX_RETRIES=5
                    RETRY_COUNT=0
                    CONNECTED=false
                    while [ ${'$'}RETRY_COUNT -lt ${'$'}MAX_RETRIES ] && [ "${'$'}CONNECTED" = "false" ]; do
                        echo "Attempting ADB connection (attempt ${'$'}((RETRY_COUNT + 1))/${'$'}MAX_RETRIES)..."
                        adb connect 127.0.0.1:5558 2>&1
                        sleep 1.5
                        if adb devices 2>&1 | grep -q "127.0.0.1:5558.*device"; then
                            CONNECTED=true
                            echo "✓ ADB connected to localhost:5558"
                            break
                        else
                            RETRY_COUNT=${'$'}((RETRY_COUNT + 1))
                            if [ ${'$'}RETRY_COUNT -lt ${'$'}MAX_RETRIES ]; then
                                sleep 1
                            fi
                        fi
                    done
                    # Final verification
                    if [ "${'$'}CONNECTED" = "false" ]; then
                        echo "⚠ ADB connection failed after ${'$'}MAX_RETRIES attempts"
                        echo "ADB devices status:"
                        adb devices 2>&1
                        echo "⚠ Attempting to continue anyway..."
                        # Try one more time with longer wait
                        adb connect 127.0.0.1:5558 2>&1
                        sleep 2
                        if adb devices 2>&1 | grep -q "127.0.0.1:5558.*device"; then
                            echo "✓ ADB connected on final attempt"
                            CONNECTED=true
                        fi
                    fi
                    if [ "${'$'}CONNECTED" = "true" ]; then
                        echo "✓ Droidrun environment prepared successfully"
                    else
                        echo "⚠ Droidrun environment preparation completed with warnings"
                    fi
                """.trimIndent()
                
                mainHandler.post {
                    if (terminalSession != null && !sessionFinished) {
                        terminalSession!!.write(droidrunSetupCommand)
                        terminalSession!!.write("\n")
                        Logger.logInfo(LOG_TAG, "Droidrun environment preparation started")
                        // Mark as prepared after a delay to allow command to execute
                        Thread {
                            Thread.sleep(10000) // Wait for setup to complete
                            isDroidrunPrepared = true
                            Logger.logInfo(LOG_TAG, "Droidrun environment marked as prepared")
                        }.start()
                    }
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error preparing droidrun environment", e)
            }
        }.start()
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
                    _uiState.update { it.copy(agentStateMessage = stateResult.message) }
                    updateStatus(stateResult.message)
                    updateNotification()
                    
                    // Start overlay service when task begins
                    startOverlayService()
                }
                is TaskExecutorStateManager.StateTransitionResult.Error -> {
                    Logger.logWarn(LOG_TAG, "State transition failed: ${stateResult.message}")
                    updateStatus(stateResult.message)
                    return
                }
            }
            
            // Quick verification that ADB is still connected (non-blocking check)
            // If droidrun environment wasn't prepared, prepare it now (async, non-blocking)
            if (!isDroidrunPrepared) {
                Logger.logWarn(LOG_TAG, "Droidrun environment not prepared, preparing now...")
                prepareDroidrunEnvironment()
                // Note: prepareDroidrunEnvironment() runs asynchronously
                // The command below will handle reconnection if needed
            }
            
            // Simplified command: set ANDROID_SERIAL, ensure API key is set, and run droidrun
            // ADB setup should already be done by prepareDroidrunEnvironment()
            // Only connect if no device is connected (simple check)
            val apiKeyExport = if (googleApiKey.isNotBlank()) {
                "export GOOGLE_API_KEY=\"$googleApiKey\" && "
            } else {
                ""
            }
            
            // Build droidrun command with all configured flags
            val droidrunFlags = droidRunConfig.buildFlagsString()
            val deviceSerial = droidRunConfig.device ?: "127.0.0.1:5558"
            
            val droidrunCommand = """
                export ANDROID_SERIAL="$deviceSerial"
                adb devices 2>&1 | grep -q "$deviceSerial.*device" || adb connect $deviceSerial 2>&1 || true
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
                    
                    // Stop overlay service when task stops
                    stopOverlayService()
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
                        
                        // Stop overlay service when task completes
                        stopOverlayService()
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
                        
                        // Stop overlay service when task fails
                        stopOverlayService()
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
        // Update overlay service with output logs
        TaskExecutorOverlayService.updateLogs(output)
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
        // Update overlay service with current task status
        TaskExecutorOverlayService.updateTaskStatus(status)
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

