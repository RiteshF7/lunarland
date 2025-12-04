package com.termux.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.NonNull
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.BuildConfig
import com.termux.R
import com.termux.shared.logger.Logger
import com.termux.shared.notification.NotificationUtils
import com.termux.shared.shell.ShellUtils
import com.termux.app.TermuxService
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession.TermuxSessionClient
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Activity that provides a minimal UI for executing commands inside a persistent Termux shell session.
 * Uses TermuxService for stable background execution and shows progress in notifications.
 */
class TaskExecutorActivity : ComponentActivity(), TermuxSessionClient, ServiceConnection {

    private val LOG_TAG = "TaskExecutorActivity"

    private val sessionIds = AtomicInteger()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val viewModel: TaskExecutorViewModel by lazy {
        ViewModelProvider(this)[TaskExecutorViewModel::class.java]
    }

    private var termuxService: TermuxService? = null
    private var currentSession: TermuxSession? = null
    private var terminalSession: TerminalSession? = null
    private var sessionClient: TaskExecutorSessionClient? = null
    private var sessionFinished = false
    private var isServiceBound = false
    private var currentTaskCommand: String? = null
    private var taskStartTime: Long = 0
    private val stopTaskReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.termux.app.ACTION_STOP_TASK") {
                stopCurrentTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TermuxShellEnvironment.init(applicationContext)

        setContent {
            TaskExecutorScreen(
                viewModel = viewModel,
                onDispatchCommand = { command -> dispatchCommand(command) },
                onRestartSession = { restartSession() },
                onCloseSession = { closeSession() },
                onRunInstallScript = { runInstallScript() },
                onRunInitSetupScript = { runInitSetupScript() }
            )
        }

        // Start and bind to TermuxService for stable background execution
        startAndBindService()
        
        // Setup notification channel
        setupNotificationChannel()
        
        // Register receiver for stop task action
        registerReceiver(stopTaskReceiver, IntentFilter("com.termux.app.ACTION_STOP_TASK"))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopTaskReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        unbindService()
        tearDownSession()
        // Cancel notification when activity is destroyed
        cancelNotification()
        // Stop overlay service
        stopOverlayService()
    }

    private fun startAndBindService() {
        try {
            // Start the TermuxService and make it run regardless of who is bound to it
            val serviceIntent = Intent(this, TermuxService::class.java)
            startService(serviceIntent)

            // Attempt to bind to the service
            if (!bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)) {
                throw RuntimeException("bindService() failed")
            }
            isServiceBound = true
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TaskExecutorActivity failed to start TermuxService", e)
            viewModel.updateStatus("Failed to start service: ${e.message}")
        }
    }

    private fun unbindService() {
        if (isServiceBound) {
            try {
                unbindService(this)
            } catch (e: Exception) {
                // ignore
            }
            isServiceBound = false
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Logger.logDebug(LOG_TAG, "onServiceConnected")
        termuxService = (service as TermuxService.LocalBinder).service
        prepareSession()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected")
        termuxService = null
    }

    private fun prepareSession() {
        viewModel.updateStatus(getString(R.string.driver_status_bootstrapping))
        viewModel.setUiEnabled(false)
        TermuxInstaller.setupBootstrapIfNeeded(this) {
            runOnUiThread {
                startNewSession()
            }
        }
    }

    private fun startNewSession() {
        if (termuxService == null) {
            Logger.logError(LOG_TAG, "TermuxService not available")
            viewModel.updateStatus("Service not available")
            return
        }

        tearDownSession()
        val client = TaskExecutorSessionClient { transcript ->
            mainHandler.post {
                viewModel.updateOutput(transcript)
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

        // Use TermuxService's createTermuxSession method to ensure the session is properly
        // registered with the service's session manager for stable background execution
        // This is critical - sessions must be in mTermuxSessions list to survive background
        currentSession = termuxService!!.createTermuxSession(executionCommand)
        
        if (currentSession == null) {
            Logger.logError(LOG_TAG, "Failed to create session via service, trying direct creation")
            // Fallback: create directly and manually add to service manager
            currentSession = TermuxSession.execute(
                termuxService!!, // Use service context for proper session management
                executionCommand,
                client,
                this,
                TermuxShellEnvironment(),
                null,
                false
            )
            // Manually add to service's session manager if direct creation succeeded
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
            // Session was created via service, update the terminal session client to use our custom client
            // This ensures we get transcript updates
            currentSession!!.getTerminalSession().updateTerminalSessionClient(client)
        }

        if (currentSession == null) {
            Logger.logError(LOG_TAG, "Failed to create task executor session")
            mainHandler.post {
                viewModel.updateStatus(getString(R.string.task_executor_status_failed, "Unable to start shell"))
            }
            return
        }

        terminalSession = currentSession!!.getTerminalSession()
        sessionFinished = false
        viewModel.resetSessionState()
        try {
            // Use a reasonable default terminal geometry to bootstrap the session.
            terminalSession!!.initializeEmulator(80, 24, 8, 16)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Unable to initialize terminal emulator", e)
            mainHandler.post {
                viewModel.updateStatus(getString(R.string.task_executor_status_failed, e.message ?: "Unknown error"))
            }
            return
        }

        mainHandler.post {
            viewModel.setUiEnabled(true)
            viewModel.updateStatus(getString(R.string.task_executor_status_ready))
        }
        client.refreshTranscript(terminalSession!!)
        
        // Setup ADB environment for droidrun to detect device
        setupAdbEnvironment()
        
        // Setup Google API Key globally
        setupGoogleApiKey()
    }
    
    private fun setupAdbEnvironment() {
        if (terminalSession == null || sessionFinished) {
            return
        }
        
        Thread {
            try {
                // Setup ADB environment variables so droidrun can detect the device
                // TMPDIR is needed for ADB daemon to start
                // ANDROID_SERIAL points to localhost TCP connection
                val homeVar = "\$HOME"
                val tmpdirVar = "\$TMPDIR"
                val adbSetupCommand = """
                    export TMPDIR="$homeVar/usr/tmp"
                    mkdir -p "$tmpdirVar"
                    export ANDROID_SERIAL="127.0.0.1:5558"
                    # Add to .bashrc if not already present
                    if ! grep -q "export TMPDIR" "$homeVar/.bashrc" 2>/dev/null; then
                        echo 'export TMPDIR="$homeVar/usr/tmp"' >> "$homeVar/.bashrc"
                        mkdir -p "$homeVar/usr/tmp"
                    fi
                    if ! grep -q "export ANDROID_SERIAL" "$homeVar/.bashrc" 2>/dev/null; then
                        echo 'export ANDROID_SERIAL="127.0.0.1:5558"' >> "$homeVar/.bashrc"
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
        if (terminalSession == null || sessionFinished) {
            return
        }
        
        Thread {
            try {
                // First check if GOOGLE_API_KEY exists in local.properties (via BuildConfig)
                val apiKeyFromProps = BuildConfig.GOOGLE_API_KEY
                
                if (apiKeyFromProps.isBlank()) {
                    mainHandler.post {
                        viewModel.updateStatus(getString(R.string.task_executor_api_key_missing))
                        Logger.logError(LOG_TAG, "GOOGLE_API_KEY not found in local.properties")
                    }
                    return@Thread
                }
                
                // Check if GOOGLE_API_KEY is already set in current shell environment
                // If not, export it from local.properties and add to .bashrc for future sessions
                // Also disable telemetry to prevent network errors from cluttering output
                val profilePath = "\$HOME/.bashrc"
                val apiKeyVar = "GOOGLE_API_KEY"
                val telemetryVar = "DROIDRUN_TELEMETRY_ENABLED"
                val setupCommand = """
                    if [ -z "$$apiKeyVar" ]; then
                        # Export for current session
                        export $apiKeyVar="$apiKeyFromProps"
                        # Add to .bashrc for all future sessions (if not already present)
                        if ! grep -q "export $apiKeyVar" $profilePath 2>/dev/null; then
                            echo 'export $apiKeyVar="$apiKeyFromProps"' >> $profilePath
                        fi
                        echo "$apiKeyVar exported from local.properties"
                    else
                        echo "$apiKeyVar already set in environment"
                    fi
                    # Disable telemetry to prevent network errors from appearing after task completion
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
                mainHandler.post {
                    viewModel.updateStatus(getString(R.string.task_executor_api_key_error, e.message ?: "Unknown error"))
                }
            }
        }.start()
    }

    private fun dispatchCommand(command: String) {
        if (terminalSession == null || sessionFinished) {
            return
        }

        if (command.isBlank()) {
            return
        }

        // Track current task
        currentTaskCommand = command
        taskStartTime = System.currentTimeMillis()
        viewModel.updateTaskState(command, 0, true)
        updateNotification()
        
        // Start overlay service to show logs
        startOverlayService()

        // Wrap command in droidrun run format
        // Telemetry is already disabled in setupGoogleApiKey() to prevent background network errors
        val droidrunCommand = "droidrun run \"$command\""
        terminalSession!!.write(droidrunCommand)
        terminalSession!!.write("\n")
    }
    
    private fun stopCurrentTask() {
        if (terminalSession != null && !sessionFinished && currentTaskCommand != null) {
            // Send Ctrl+C to stop the current task
            terminalSession!!.write("\u0003") // Ctrl+C character
            Logger.logInfo(LOG_TAG, "Stopped task: $currentTaskCommand")
            
            // Update state
            currentTaskCommand = null
            viewModel.updateTaskState(null, 0, false)
            updateNotification()
            stopOverlayService()
        }
    }
    
    private fun startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Logger.logWarn(LOG_TAG, "Overlay permission not granted")
            return
        }
        
        // Set stop callback
        TaskExecutorOverlayService.setStopCallback { stopCurrentTask() }
        
        // Start overlay service
        val intent = Intent(this, TaskExecutorOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopOverlayService() {
        val intent = Intent(this, TaskExecutorOverlayService::class.java)
        stopService(intent)
        TaskExecutorOverlayService.setStopCallback(null)
    }
    
    private fun closeSession() {
        tearDownSession()
        finish()
    }

    private fun restartSession() {
        startNewSession()
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
            viewModel.resetSessionState()
            viewModel.clearOutput()
        }
    }

    private fun runInstallScript() {
        if (terminalSession == null || sessionFinished) {
            return
        }

        // Extract script from assets and execute it
        Thread {
            try {
                val scriptPath = extractScriptFromAssets("setup_lunar_adb_agent.sh")
                if (scriptPath != null) {
                    // Make script executable and run it with bash
                    val command = "chmod +x '$scriptPath' && bash '$scriptPath'"
                    mainHandler.post {
                        if (terminalSession != null && !sessionFinished) {
                            terminalSession!!.write(command)
                            terminalSession!!.write("\n")
                        }
                    }
                } else {
                    mainHandler.post {
                        Logger.logError(LOG_TAG, "Failed to extract setup script from assets")
                    }
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error running setup script", e)
            }
        }.start()
    }

    private fun extractScriptFromAssets(assetName: String): String? {
        val assetManager = assets
        val scriptFile = File(TermuxConstants.TERMUX_HOME_DIR_PATH, assetName)

        try {
            // Create parent directory if it doesn't exist
            val parentDir = scriptFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            // Copy asset to file
            val inputStream: InputStream = assetManager.open(assetName)
            val outputStream: OutputStream = FileOutputStream(scriptFile)

            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }

            outputStream.close()
            inputStream.close()

            // Make file executable
            scriptFile.setExecutable(true, false)

            Logger.logInfo(LOG_TAG, "Extracted script to: " + scriptFile.absolutePath)
            return scriptFile.absolutePath
        } catch (e: IOException) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to extract script from assets", e)
            return null
        }
    }

    override fun onTermuxSessionExited(session: TermuxSession) {
        val exitCode = session.executionCommand.resultData.exitCode
        runOnUiThread {
            sessionFinished = true
            val code = exitCode ?: -1
            viewModel.setSessionFinished(true, code)
            viewModel.updateStatus(getString(R.string.task_executor_status_finished, code))
            
            // Clear task state
            currentTaskCommand = null
            viewModel.updateTaskState(null, 0, false)
            updateNotification()
            
            Logger.logInfo(LOG_TAG, "Session finished with exit code: $code")
        }
    }

    private fun runInitSetupScript() {
        if (terminalSession == null || sessionFinished) {
            return
        }

        Thread {
            try {
                val scriptPath = extractScriptFromAssets("setup_lunar_adb_agent.sh")
                if (scriptPath != null) {
                    // Place script into a bin directory on PATH, ensure executable, then run it.
                    val command =
                        "mkdir -p \"\$HOME/bin\" && " +
                        "cp '$scriptPath' \"\$HOME/bin/setup_lunar_adb_agent.sh\" && " +
                        "chmod +x \"\$HOME/bin/setup_lunar_adb_agent.sh\" && " +
                        "bash \"\$HOME/bin/setup_lunar_adb_agent.sh\""
                    mainHandler.post {
                        if (terminalSession != null && !sessionFinished) {
                            terminalSession!!.write(command)
                            terminalSession!!.write("\n")
                        }
                    }
                } else {
                    mainHandler.post {
                        Logger.logError(LOG_TAG, "Failed to extract Lunar ADB agent setup script from assets")
                    }
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error running Lunar ADB agent setup script", e)
            }
        }.start()
    }

    private inner class TaskExecutorSessionClient(
        private val onTranscriptUpdate: (String) -> Unit
    ) : TermuxTerminalSessionClientBase() {

        override fun onTextChanged(@NonNull changedSession: TerminalSession) {
            refreshTranscript(changedSession)
        }

        override fun onSessionFinished(@NonNull finishedSession: TerminalSession) {
            if (currentSession != null && !sessionFinished) {
                currentSession!!.finish()
            }
        }

        fun refreshTranscript(session: TerminalSession) {
            val transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, false)
            mainHandler.post {
                onTranscriptUpdate(transcript ?: "")
                // Update overlay logs
                if (transcript != null) {
                    TaskExecutorOverlayService.updateLogs(transcript)
                    updateTaskProgress(transcript)
                }
            }
        }
    }
    
    private fun setupNotificationChannel() {
        // No need for separate channel - using TermuxService notification
    }
    
    private fun updateTaskProgress(output: String) {
        if (currentTaskCommand == null) return
        
        val outputLower = output.lowercase()
        var shouldUpdate = false
        
        // Check for task completion - multiple patterns to catch all droidrun completion messages
        val completionPatterns = listOf(
            "goal achieved",
            "goal succeeded", 
            "task completed",
            "successfully completed",
            "task marked as complete",
            "complete(success=true",
            "complete(success = true",
            "code execution successful"
        )
        
        val failurePatterns = listOf(
            "goal failed",
            "task failed",
            "complete(success=false",
            "complete(success = false"
        )
        
        val isCompleted = completionPatterns.any { outputLower.contains(it) }
        val isFailed = failurePatterns.any { outputLower.contains(it) }
        
        // Also check if command prompt has returned (indicates command finished)
        // Look for prompt patterns like "$ ", "> ", or shell prompt at end of output
        val lastLines = output.lines().takeLast(3).joinToString("\n").lowercase()
        val promptReturned = lastLines.matches(Regex(".*[\\$>]#\\s*$")) || 
                             (lastLines.contains("$") && !lastLines.contains("droidrun run"))
        
        when {
            isCompleted -> {
                Logger.logInfo(LOG_TAG, "Task completed detected: $currentTaskCommand")
                currentTaskCommand = null
                viewModel.updateTaskState(null, 0, false)
                shouldUpdate = true
                // Stop overlay after a short delay to show completion message
                mainHandler.postDelayed({ stopOverlayService() }, 2000)
            }
            isFailed -> {
                Logger.logInfo(LOG_TAG, "Task failed detected: $currentTaskCommand")
                currentTaskCommand = null
                viewModel.updateTaskState(null, 0, false)
                shouldUpdate = true
                // Stop overlay after a short delay to show failure message
                mainHandler.postDelayed({ stopOverlayService() }, 2000)
            }
            promptReturned && !outputLower.contains("droidrun run") -> {
                // Command prompt returned but no droidrun command visible - task likely finished
                // Wait a bit to see if completion message appears, but if it's been a while, mark as done
                val elapsed = System.currentTimeMillis() - taskStartTime
                if (elapsed > 2000) { // Wait 2 seconds after prompt returns
                    Logger.logInfo(LOG_TAG, "Command prompt returned, marking task as complete: $currentTaskCommand")
                    currentTaskCommand = null
                    viewModel.updateTaskState(null, 0, false)
                    shouldUpdate = true
                }
            }
            else -> {
                // Task is still running - keep it as running
                val currentState = viewModel.uiState.value
                if (!currentState.isTaskRunning || currentState.currentTask != currentTaskCommand) {
                    viewModel.updateTaskState(currentTaskCommand, 0, true)
                    shouldUpdate = true
                }
            }
        }
        
        if (shouldUpdate) {
            updateNotification()
        }
    }
    
    private fun updateNotification() {
        // Update TermuxService notification instead of creating separate one
        val uiState = viewModel.uiState.value
        TermuxService.setTaskExecutorState(
            uiState.currentTask,
            uiState.taskProgress,
            uiState.isTaskRunning
        )
    }
    
    private fun cancelNotification() {
        // Clear task executor state in TermuxService notification
        TermuxService.setTaskExecutorState(null, 0, false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskExecutorScreen(
    viewModel: TaskExecutorViewModel,
    onDispatchCommand: (String) -> Unit,
    onRestartSession: () -> Unit,
    onCloseSession: () -> Unit,
    onRunInstallScript: () -> Unit,
    onRunInitSetupScript: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var commandText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when output changes
    LaunchedEffect(uiState.outputText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_executor_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status text
            Text(
                text = uiState.statusText.ifEmpty { stringResource(R.string.task_executor_status_initial) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            // Output scrollable area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(8.dp)
                ) {
                    Text(
                        text = uiState.outputText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Command input
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                label = { Text(stringResource(R.string.task_executor_generic_command_hint)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isUiEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandText.isNotBlank()) {
                            onDispatchCommand(commandText)
                            commandText = ""
                        }
                    }
                )
            )

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRunInstallScript,
                    enabled = uiState.isUiEnabled,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.task_executor_install_button))
                }

                Button(
                    onClick = onRunInitSetupScript,
                    enabled = uiState.isUiEnabled,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.task_executor_initsetup_button))
                }

                Button(
                    onClick = onRestartSession,
                    enabled = true,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.task_executor_reset_button))
                }

                Button(
                    onClick = onCloseSession,
                    enabled = true,
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(stringResource(R.string.task_executor_close_button))
                }

                Button(
                    onClick = {
                        if (commandText.isNotBlank()) {
                            onDispatchCommand(commandText)
                            commandText = ""
                        }
                    },
                    enabled = uiState.isUiEnabled,
                ) {
                    Text(stringResource(R.string.task_executor_execute_button))
                }
            }
        }
    }
}

