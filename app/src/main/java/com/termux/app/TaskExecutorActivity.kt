package com.termux.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.termux.shared.shell.ShellUtils
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
 * It keeps the same session alive across multiple commands, which allows directory changes and environment
 * mutations to persist until the user resets the session.
 */
class TaskExecutorActivity : ComponentActivity(), TermuxSessionClient {

    private val LOG_TAG = "TaskExecutorActivity"

    private val sessionIds = AtomicInteger()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val viewModel: TaskExecutorViewModel by lazy {
        ViewModelProvider(this)[TaskExecutorViewModel::class.java]
    }

    private var currentSession: TermuxSession? = null
    private var terminalSession: TerminalSession? = null
    private var sessionClient: TaskExecutorSessionClient? = null
    private var sessionFinished = false

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

        prepareSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        tearDownSession()
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

        currentSession = TermuxSession.execute(
            applicationContext,
            executionCommand,
            client,
            this,
            TermuxShellEnvironment(),
            null,
            false
        )

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
        
        // Setup Google API Key globally
        setupGoogleApiKey()
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
                val profilePath = "\$HOME/.bashrc"
                val apiKeyVar = "GOOGLE_API_KEY"
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

        // Wrap command in droidrun run format
        val droidrunCommand = "droidrun run \"$command\""
        terminalSession!!.write(droidrunCommand)
        terminalSession!!.write("\n")
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
            }
        }
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

