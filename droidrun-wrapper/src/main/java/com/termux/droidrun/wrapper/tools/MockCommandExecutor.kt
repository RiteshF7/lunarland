package com.termux.droidrun.wrapper.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * MockCommandExecutor - Simulates Python execution for testing
 * 
 * This allows you to test the agent flow and analyze logs without
 * implementing the full Termux integration.
 * 
 * It simulates:
 * - Python3 availability check
 * - pip3 package checks
 * - Python script execution with mock responses
 */
class MockCommandExecutor : CommandExecutor {
    companion object {
        private const val TAG = "MockCommandExecutor"
    }

    override suspend fun execute(command: String, args: List<String>): CommandResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔵 EXECUTE: $command ${args.joinToString(" ")}")
        
        when (command) {
            "python3" -> {
                when {
                    args.contains("--version") -> {
                        Log.i(TAG, "✅ Python version check: Python 3.11.0")
                        delay(100) // Simulate execution time
                        CommandResult.success("Python 3.11.0\n", 0)
                    }
                    else -> {
                        Log.w(TAG, "⚠️ Unknown python3 command: ${args.joinToString(" ")}")
                        CommandResult.failure("Unknown command", 1)
                    }
                }
            }
            "pip3" -> {
                when {
                    args.contains("show") && args.contains("droidrun") -> {
                        Log.i(TAG, "✅ droidrun package found")
                        delay(100)
                        CommandResult.success("Name: droidrun\nVersion: 1.0.0\n", 0)
                    }
                    args.contains("install") && args.contains("droidrun") -> {
                        Log.i(TAG, "✅ Installing droidrun package (simulated)")
                        delay(500) // Simulate install time
                        CommandResult.success("Successfully installed droidrun\n", 0)
                    }
                    else -> {
                        Log.w(TAG, "⚠️ Unknown pip3 command: ${args.joinToString(" ")}")
                        CommandResult.failure("Unknown command", 1)
                    }
                }
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown command: $command")
                CommandResult.failure("Unknown command: $command", 1)
            }
        }
    }

    override suspend fun executePython(script: String, env: Map<String, String>): CommandResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔵 EXECUTE_PYTHON: $script")
        Log.d(TAG, "📦 Environment variables:")
        env.forEach { (key, value) ->
            // Mask API keys in logs
            val displayValue = if (key.contains("API_KEY", ignoreCase = true)) {
                "${value.take(4)}...${value.takeLast(4)}"
            } else {
                value
            }
            Log.d(TAG, "   $key=$displayValue")
        }
        
        // Simulate Python script execution
        delay(1000) // Simulate execution time
        
        // Read the script to understand what it's trying to do
        try {
            val scriptContent = java.io.File(script).readText()
            Log.d(TAG, "📄 Script content (first 500 chars):")
            Log.d(TAG, scriptContent.take(500))
            
            // Simulate agent execution
            Log.i(TAG, "🤖 Simulating DroidAgent execution...")
            
            // Simulate agent steps
            val mockSteps = listOf(
                "Agent initialized",
                "Loading config from: ${env["DROIDRUN_CONFIG"]}",
                "Goal: ${env["DROIDRUN_GOAL"]}",
                "StateBridge files:",
                "  - State: ${env["DROIDRUN_STATE_FILE"]}",
                "  - Actions: ${env["DROIDRUN_ACTIONS_FILE"]}",
                "  - Results: ${env["DROIDRUN_RESULT_FILE"]}",
                "Agent running (simulated)...",
                "Step 1: Getting device state",
                "Step 2: Analyzing UI elements",
                "Step 3: Planning actions",
                "Step 4: Executing actions",
                "Agent completed successfully"
            )
            
            mockSteps.forEach { step ->
                Log.i(TAG, "   $step")
                delay(200)
            }
            
            // Return mock success response
            val mockOutput = """
                {"type": "status", "message": "Agent initialized"}
                {"type": "log", "message": "Loading configuration..."}
                {"type": "log", "message": "Goal: ${env["DROIDRUN_GOAL"]}"}
                {"type": "log", "message": "StateBridge initialized"}
                {"type": "log", "message": "Getting device state..."}
                {"type": "log", "message": "Found 15 UI elements"}
                {"type": "log", "message": "Planning actions..."}
                {"type": "log", "message": "Executing action: tap element at index 5"}
                {"type": "log", "message": "Action completed successfully"}
                {"type": "status", "message": "Agent running..."}
                {"type": "complete", "success": true, "result": "Task completed successfully (simulated)"}
            """.trimIndent()
            
            Log.i(TAG, "✅ Python script execution completed (simulated)")
            CommandResult.success(mockOutput, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading script: ${e.message}", e)
            val errorOutput = """{"type": "error", "message": "Script execution error: ${e.message}"}"""
            CommandResult.failure(errorOutput, 1)
        }
    }
}

