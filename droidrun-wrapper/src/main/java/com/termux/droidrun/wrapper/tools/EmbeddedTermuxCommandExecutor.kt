package com.termux.droidrun.wrapper.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * EmbeddedTermuxCommandExecutor - Executes commands using ProcessBuilder
 * 
 * This implementation uses Android's ProcessBuilder to execute commands,
 * with support for Python execution when available.
 * 
 * Advantages:
 * - No external app dependency
 * - No permission issues
 * - Works with standard Android APIs
 * - Simple and reliable
 */
class EmbeddedTermuxCommandExecutor(
    private val context: Context
) : CommandExecutor {
    
    companion object {
        private const val TAG = "EmbeddedTermuxExecutor"
    }
    
    /**
     * Execute a shell command using ProcessBuilder
     */
    override suspend fun execute(command: String, args: List<String>): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Executing command: $command ${args.joinToString(" ")}")
            
            // All commands use ProcessBuilder
            executeViaProcessBuilder(command, args)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}", e)
            CommandResult.failure("Error executing command: ${e.message}", -1)
        }
    }
    
    /**
     * Execute a Python script using ProcessBuilder
     */
    override suspend fun executePython(script: String, args: Map<String, String>): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== executePython() called ===")
            Log.d(TAG, "Script path: $script")
            Log.d(TAG, "Environment variables: ${args.keys.joinToString(", ")}")
            
            // Check if script file exists
            val scriptFile = File(script)
            if (!scriptFile.exists()) {
                Log.e(TAG, "Script file does not exist: $script")
                return@withContext CommandResult.failure("Script file does not exist: $script", -1)
            }
            
            // Build Python command
            val pythonCommand = listOf("python3", scriptFile.absolutePath)
            
            // Execute via ProcessBuilder with environment variables
            executeViaProcessBuilderWithEnv(pythonCommand, args)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Python script: ${e.message}", e)
            CommandResult.failure("Error executing Python script: ${e.message}", -1)
        }
    }
    
    /**
     * Execute command via ProcessBuilder (for shell commands)
     */
    private suspend fun executeViaProcessBuilder(command: String, args: List<String>): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing via ProcessBuilder: $command ${args.joinToString(" ")}")
            
            val commandArray = mutableListOf<String>().apply {
                add(command)
                addAll(args)
            }
            
            val process = ProcessBuilder(*commandArray.toTypedArray())
                .redirectErrorStream(false)
                .start()
            
            // Capture output
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            
            // Wait for completion
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Command completed: exitCode=$exitCode")
            Log.v(TAG, "stdout: ${stdout.take(200)}")
            Log.v(TAG, "stderr: ${stderr.take(200)}")
            
            CommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                success = exitCode == 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "ProcessBuilder error: ${e.message}", e)
            CommandResult.failure("ProcessBuilder error: ${e.message}", -1)
        }
    }
    
    /**
     * Execute command via ProcessBuilder with environment variables
     */
    private suspend fun executeViaProcessBuilderWithEnv(
        command: List<String>,
        env: Map<String, String>
    ): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing via ProcessBuilder with env: ${command.joinToString(" ")}")
            
            // Set up environment
            val processEnv = mutableMapOf<String, String>()
            processEnv.putAll(System.getenv())
            processEnv.putAll(env)
            
            val process = ProcessBuilder(*command.toTypedArray())
                .directory(context.filesDir) // Use app's files directory
                .redirectErrorStream(false)
                .apply {
                    environment().clear()
                    environment().putAll(processEnv)
                }
                .start()
            
            // Capture output
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            
            // Wait for completion
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Command completed: exitCode=$exitCode")
            Log.v(TAG, "stdout length: ${stdout.length}, stderr length: ${stderr.length}")
            
            CommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                success = exitCode == 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "ProcessBuilder error: ${e.message}", e)
            CommandResult.failure("ProcessBuilder error: ${e.message}", -1)
        }
    }
}

