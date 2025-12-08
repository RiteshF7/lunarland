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
import com.termux.app.taskexecutor.model.TaskExecutorUiState
import com.termux.app.taskexecutor.model.TaskStatus
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
            currentTaskCommand = command
            taskStartTime = System.currentTimeMillis()
            updateTaskState(command, 0, true, TaskStatus.RUNNING)
            updateStatus("Executing: $command")
            updateNotification()
            
            // Ensure ANDROID_SERIAL is set to localhost and ADB is connected before running droidrun
            // This ensures droidrun connects to localhost instead of looking for a physical device
            val droidrunCommand = """
                export ANDROID_SERIAL="127.0.0.1:5558"
                # Kill and restart ADB server
                echo "Restarting ADB server..."
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
                # Run droidrun with ANDROID_SERIAL set
                if [ "${'$'}CONNECTED" = "true" ]; then
                    echo "Running droidrun with ADB connected..."
                    droidrun run "$command"
                else
                    echo "⚠ Warning: ADB not connected, but attempting to run droidrun anyway..."
                    echo "⚠ droidrun will use ANDROID_SERIAL=127.0.0.1:5558"
                    droidrun run "$command" || {
                        echo "❌ Error: droidrun failed - ADB device not connected"
                        echo "Please ensure ADB is properly configured"
                        exit 1
                    }
                fi
            """.trimIndent()
            
            Logger.logInfo(LOG_TAG, "Writing command to terminal with ANDROID_SERIAL set to localhost")
            Logger.logInfo(LOG_TAG, "Command: $droidrunCommand")
            terminalSession!!.write(droidrunCommand)
            terminalSession!!.write("\n")
            Logger.logInfo(LOG_TAG, "Command written successfully to terminal session")
            
            // Verify the command was written
            Logger.logInfo(LOG_TAG, "Terminal session state: isRunning=${terminalSession!!.isRunning()}")
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in dispatchCommand", e)
            updateStatus("Error: ${e.message}")
            updateTaskState(null, 0, false, TaskStatus.ERROR)
        }
    }
    
    fun resetSession() {
        startNewSession()
    }
    
    fun toggleLogsVisibility() {
        _uiState.update { it.copy(showLogs = !it.showLogs) }
    }
    
    fun stopCurrentTask() {
        if (terminalSession != null && !sessionFinished && currentTaskCommand != null) {
            try {
                Logger.logInfo(LOG_TAG, "Stopping task: $currentTaskCommand")
                
                // Send Ctrl+C multiple times to ensure the command is interrupted
                // This is important for droidrun which may be in a subprocess
                terminalSession!!.write("\u0003") // First Ctrl+C
                Thread.sleep(100)
                terminalSession!!.write("\u0003") // Second Ctrl+C (in case first didn't work)
                Thread.sleep(100)
                
                // Also try sending Enter to confirm cancellation if needed
                terminalSession!!.write("\n")
                
                Logger.logInfo(LOG_TAG, "Sent stop signals to terminal")
                
                // Update state immediately
                currentTaskCommand = null
                updateTaskState(null, 0, false, TaskStatus.STOPPED)
                updateStatus("Task stopped")
                updateNotification()
                
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
            Logger.logWarn(LOG_TAG, "Cannot stop task: terminalSession=${terminalSession != null}, sessionFinished=$sessionFinished, currentTaskCommand=$currentTaskCommand")
        }
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
        if (currentTaskCommand == null) {
            // If no task is running but status is RUNNING, reset it
            if (_uiState.value.taskStatus == TaskStatus.RUNNING) {
                Logger.logWarn(LOG_TAG, "Task status is RUNNING but no currentTaskCommand, resetting to STOPPED")
                updateTaskState(null, 0, false, TaskStatus.STOPPED)
                updateStatus("Ready")
            }
            return
        }
        
        val outputLower = output.lowercase()
        var shouldUpdate = false
        
        Logger.logDebug(LOG_TAG, "updateTaskProgress: checking output for task: $currentTaskCommand")
        
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
                updateStatus("Task completed successfully")
                shouldUpdate = true
            }
            isFailed -> {
                Logger.logInfo(LOG_TAG, "Task failed detected: $currentTaskCommand")
                currentTaskCommand = null
                updateTaskState(null, 0, false, TaskStatus.ERROR)
                updateStatus("Task failed")
                shouldUpdate = true
            }
            promptReturned && !outputLower.contains("droidrun run") -> {
                val elapsed = System.currentTimeMillis() - taskStartTime
                if (elapsed > 2000) {
                    Logger.logInfo(LOG_TAG, "Command prompt returned, marking task as complete: $currentTaskCommand")
                    currentTaskCommand = null
                    updateTaskState(null, 0, false, TaskStatus.SUCCESS)
                    updateStatus("Task completed")
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
                statusText = "Ready",
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

