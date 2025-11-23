package com.termux.droidrun.wrapper.tools

import android.content.Context
import android.util.Log
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession.TermuxSessionClient
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * TermuxCommandExecutor - Executes commands using Termux's TermuxSession
 * 
 * This implementation uses Termux's native command execution APIs to run
 * shell commands and Python scripts in a proper Termux environment.
 */
class TermuxCommandExecutor(
    private val context: Context
) : CommandExecutor, TermuxSessionClient {
    
    companion object {
        private const val TAG = "TermuxCommandExecutor"
    }
    
    private var currentSession: TermuxSession? = null
    private var resultContinuation: kotlin.coroutines.Continuation<CommandResult>? = null
    
    init {
        // Initialize Termux environment
        TermuxShellEnvironment.init(context.applicationContext)
    }
    
    override suspend fun execute(command: String, args: List<String>): CommandResult = 
        withContext(Dispatchers.IO) {
            executeCommandInternal(command, args, null)
        }
    
    override suspend fun executePython(script: String, args: Map<String, String>): CommandResult = 
        withContext(Dispatchers.IO) {
            val scriptFile = java.io.File(script)
            if (!scriptFile.exists()) {
                Log.e(TAG, "Script file does not exist: $script")
                return@withContext CommandResult.failure("Script file does not exist: $script", -1)
            }
            
            // Build Python command with environment variables
            val pythonArgs = mutableListOf<String>()
            pythonArgs.add(scriptFile.absolutePath)
            
            // Add environment variables as command-line arguments if needed
            // For now, we'll pass them via the environment map
            
            executeCommandInternal("python3", pythonArgs, args)
        }
    
    private suspend fun executeCommandInternal(
        executable: String,
        arguments: List<String>,
        environment: Map<String, String>?
    ): CommandResult = suspendCancellableCoroutine { continuation ->
        try {
            resultContinuation = continuation
            
            val executionCommand = ExecutionCommand()
            executionCommand.executable = executable
            executionCommand.arguments = arguments.toTypedArray()
            executionCommand.runner = Runner.TERMINAL_SESSION.getName()
            executionCommand.commandLabel = "DroidRun Command: $executable"
            executionCommand.terminalTranscriptRows = 10000
            
            // Convert environment map to HashMap if provided
            val additionalEnv = environment?.let { HashMap(it) }
            
            // Create a minimal terminal session client
            val sessionClient = object : TermuxTerminalSessionClientBase() {
                override fun onSessionFinished(session: TerminalSession) {
                    // Session finished, result will come via onTermuxSessionExited
                    Log.d(TAG, "Terminal session finished")
                }
            }
            
            Log.d(TAG, "Executing command: $executable ${arguments.joinToString(" ")}")
            
            currentSession = TermuxSession.execute(
                context.applicationContext,
                executionCommand,
                sessionClient,
                this@TermuxCommandExecutor,
                TermuxShellEnvironment(),
                additionalEnv,
                true // setStdoutOnExit
            )
            
            if (currentSession == null) {
                Log.e(TAG, "Failed to create Termux session")
                continuation.resume(CommandResult.failure("Failed to create Termux session. Make sure Termux environment is initialized.", -1))
                resultContinuation = null
                return@suspendCancellableCoroutine
            }
            
            // Add timeout - if callback doesn't fire within 30 seconds, fail
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(30000)
                if (resultContinuation == continuation) {
                    Log.e(TAG, "Command execution timed out")
                    continuation.resume(CommandResult.failure("Command execution timed out after 30 seconds", -1))
                    resultContinuation = null
                }
            }
            
            // Result will come via onTermuxSessionExited callback
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
            continuation.resume(CommandResult.failure("Error: ${e.message}", -1))
            resultContinuation = null
        }
    }
    
    override fun onTermuxSessionExited(session: TermuxSession) {
        val executionCommand = session.executionCommand
        val exitCode = executionCommand.resultData.exitCode ?: -1
        val stdout = executionCommand.resultData.stdout?.toString() ?: ""
        val stderr = executionCommand.resultData.stderr?.toString() ?: ""
        
        Log.d(TAG, "Command completed: exitCode=$exitCode, stdoutLength=${stdout.length}, stderrLength=${stderr.length}")
        
        val result = CommandResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            success = exitCode == 0
        )
        
        resultContinuation?.resume(result)
        resultContinuation = null
        currentSession = null
    }
}

