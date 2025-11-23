package com.termux.droidrun.wrapper.bridge

import android.content.Context
import android.util.Log
import com.termux.droidrun.wrapper.tools.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Agent event types
 */
sealed class AgentEvent {
    data class Log(val message: String) : AgentEvent()
    data class Status(val message: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data class Complete(val success: Boolean, val result: String) : AgentEvent()
}

/**
 * PythonBridge - Executes Python DroidAgent code and streams events
 * 
 * This class manages Python execution via CommandExecutor (Termux)
 * and streams agent events back to the Android app.
 */
class PythonBridge(
    private val context: Context,
    private val commandExecutor: CommandExecutor,
    private val stateBridge: StateBridge
) {
    companion object {
        private const val TAG = "PythonBridge"
        private const val PYTHON_SCRIPT_NAME = "droidrun_wrapper.py"
        private const val WHEELS_DIR_NAME = "wheels"
        private const val MAIN_WHEEL_PREFIX = "droidrun-"
    }
    
    private var isRunning = false
    private var currentProcess: Process? = null
    
    /**
     * Initialize Python environment
     * Checks if Python is available and installs droidrun package if needed
     */
    suspend fun initializePythonEnvironment(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Python environment...")
            
            // Check if Python is installed
            // User needs to install it manually: pkg install python
            Log.d(TAG, "Checking if Python is installed...")
            
            // Try using full path first (in case PATH isn't set correctly)
            val actualFilesDir = context.filesDir.absolutePath
            val pythonPath = "$actualFilesDir/usr/bin/python3"
            val pythonFile = java.io.File(pythonPath)
            
            var pythonCheck = if (pythonFile.exists() && pythonFile.canExecute()) {
                // Try full path first
                commandExecutor.execute(pythonPath, listOf("--version"))
            } else {
                // Fall back to python3 in PATH
                commandExecutor.execute("python3", listOf("--version"))
            }
            
            if (!pythonCheck.success) {
                Log.e(TAG, "Python3 not available: ${pythonCheck.stderr}")
                Log.e(TAG, "Python is not installed.")
                Log.e(TAG, "To install Python, run: pkg install python")
                Log.e(TAG, "You can use TaskExecutorActivity or DriverActivity to install it manually.")
                return@withContext false
            }
            
            Log.d(TAG, "Python version: ${pythonCheck.stdout}")
            
            // Check if droidrun package is installed
            val pipCheck = commandExecutor.execute("pip3", listOf("show", "droidrun"))
            if (!pipCheck.success) {
                Log.d(TAG, "droidrun package not found, installing from local wheels...")
                
                // Extract wheels from assets
                val wheelsDir = extractWheelsFromAssets()
                if (wheelsDir == null) {
                    Log.e(TAG, "Failed to extract wheels from assets")
                    return@withContext false
                }
                
                // Install dependency wheels first
                val depWheelsDir = java.io.File(wheelsDir, WHEELS_DIR_NAME)
                if (depWheelsDir.exists() && depWheelsDir.isDirectory) {
                    val depWheels = depWheelsDir.listFiles { file ->
                        file.name.endsWith(".whl") && !file.name.startsWith(MAIN_WHEEL_PREFIX)
                    } ?: emptyArray()
                    
                    if (depWheels.isNotEmpty()) {
                        Log.d(TAG, "Installing ${depWheels.size} dependency wheels...")
                        for (wheel in depWheels) {
                            val installResult = commandExecutor.execute(
                                "pip3",
                                listOf("install", "--no-index", "--find-links", depWheelsDir.absolutePath, wheel.name)
                            )
                            if (!installResult.success) {
                                Log.w(TAG, "Failed to install dependency ${wheel.name}: ${installResult.stderr}")
                                // Continue with other dependencies
                            } else {
                                Log.d(TAG, "Installed dependency: ${wheel.name}")
                            }
                        }
                    }
                }
                
                // Install main droidrun wheel
                val mainWheel = wheelsDir.listFiles { file: java.io.File ->
                    file.name.endsWith(".whl") && file.name.startsWith(MAIN_WHEEL_PREFIX)
                }?.firstOrNull()
                
                if (mainWheel != null) {
                    Log.d(TAG, "Installing main droidrun wheel: ${mainWheel.name}")
                    val installResult = commandExecutor.execute(
                        "pip3",
                        listOf("install", "--force-reinstall", "--no-deps", mainWheel.absolutePath)
                    )
                    if (!installResult.success) {
                        Log.e(TAG, "Failed to install droidrun wheel: ${installResult.stderr}")
                        // Try installing with dependencies from PyPI as fallback
                        Log.d(TAG, "Trying to install dependencies from PyPI...")
                        val installWithDeps = commandExecutor.execute(
                            "pip3",
                            listOf("install", mainWheel.absolutePath)
                        )
                        if (!installWithDeps.success) {
                            Log.e(TAG, "Failed to install droidrun with dependencies: ${installWithDeps.stderr}")
                            return@withContext false
                        }
                    }
                    Log.d(TAG, "droidrun package installed successfully from wheel")
                } else {
                    Log.w(TAG, "Main droidrun wheel not found in assets, falling back to PyPI")
                    val installResult = commandExecutor.execute("pip3", listOf("install", "droidrun"))
                    if (!installResult.success) {
                        Log.e(TAG, "Failed to install droidrun from PyPI: ${installResult.stderr}")
                        return@withContext false
                    }
                    Log.d(TAG, "droidrun package installed from PyPI")
                }
            } else {
                Log.d(TAG, "droidrun package already installed")
            }
            
            // Copy wrapper script to accessible location
            copyWrapperScript()
            
            Log.d(TAG, "Python environment initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Python environment: ${e.message}", e)
            false
        }
    }
    
    /**
     * Run DroidAgent with given goal and config
     * @param goal User's goal/command
     * @param configPath Path to config YAML file
     * @return Flow of agent events (logs, status updates, etc.)
     */
    fun runDroidAgent(goal: String, configPath: String): Flow<AgentEvent> = flow {
        if (isRunning) {
            emit(AgentEvent.Error("Agent is already running"))
            return@flow
        }
        
        isRunning = true
        emit(AgentEvent.Status("Starting agent..."))
        
        try {
            // Get wrapper script path
            val scriptPath = getWrapperScriptPath()
            
            // Get API keys from ApiKeyManager (suspend function, need to call in coroutine)
            val apiKeys = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val apiKeyManager = com.termux.droidrun.wrapper.utils.ApiKeyManager(context)
                apiKeyManager.getAllApiKeys()
            }
            
            // Prepare environment variables (including API keys)
            val env = mutableMapOf(
                "DROIDRUN_GOAL" to goal,
                "DROIDRUN_CONFIG" to configPath,
                "DROIDRUN_STATE_FILE" to stateBridge.getDeviceStatePath(),
                "DROIDRUN_ACTIONS_FILE" to stateBridge.getPythonActionsPath(),
                "DROIDRUN_RESULT_FILE" to stateBridge.getActionResultPath()
            )
            
            // Add API keys to environment
            env.putAll(apiKeys)
            
            // Execute Python script
            val scriptContent = """
                |import os
                |import sys
                |import json
                |import asyncio
                |from droidrun.agent.droid import DroidAgent
                |from droidrun.config_manager import DroidrunConfig
                |
                |# Read environment variables
                |goal = os.environ.get('DROIDRUN_GOAL', '')
                |config_path = os.environ.get('DROIDRUN_CONFIG', '')
                |state_file = os.environ.get('DROIDRUN_STATE_FILE', '')
                |actions_file = os.environ.get('DROIDRUN_ACTIONS_FILE', '')
                |result_file = os.environ.get('DROIDRUN_RESULT_FILE', '')
                |
                |# Load config
                |config = DroidrunConfig.from_yaml(config_path)
                |
                |# Create AndroidTools wrapper (will be implemented in Python)
                |from droidrun_wrapper_tools import AndroidToolsWrapper
                |tools = AndroidToolsWrapper(state_file, actions_file, result_file)
                |
                |# Create and run agent
                |agent = DroidAgent(goal=goal, config=config, tools=tools)
                |
                |try:
                |    result = asyncio.run(agent.run())
                |    print(json.dumps({"type": "complete", "success": True, "result": str(result)}))
                |except Exception as e:
                |    print(json.dumps({"type": "error", "message": str(e)}))
                |    sys.exit(1)
            """.trimMargin()
            
            // Write script to temp file
            val tempScript = File(context.cacheDir, "run_agent_${System.currentTimeMillis()}.py")
            tempScript.writeText(scriptContent)
            
            // Execute script
            val result = commandExecutor.executePython(tempScript.absolutePath, env)
            
            // Parse output and emit events
            result.stdout.lines().forEach { line ->
                if (line.trim().isNotEmpty()) {
                    try {
                        // Try to parse as JSON event
                        val jsonObj = org.json.JSONObject(line.trim())
                        val type = jsonObj.optString("type", "")
                        when (type) {
                            "complete" -> {
                                val success = jsonObj.optBoolean("success", false)
                                val resultStr = jsonObj.optString("result", "")
                                emit(AgentEvent.Complete(success, resultStr))
                            }
                            "error" -> {
                                val message = jsonObj.optString("message", "Unknown error")
                                emit(AgentEvent.Error(message))
                            }
                            else -> emit(AgentEvent.Log(line))
                        }
                    } catch (e: Exception) {
                        // Not JSON, treat as log
                        emit(AgentEvent.Log(line))
                    }
                }
            }
            
            if (!result.success) {
                emit(AgentEvent.Error("Agent execution failed: ${result.stderr}"))
            } else {
                emit(AgentEvent.Status("Agent completed successfully"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error running agent: ${e.message}", e)
            emit(AgentEvent.Error("Error: ${e.message}"))
        } finally {
            isRunning = false
        }
    }
    
    /**
     * Stream agent events (logs, status updates)
     */
    fun streamAgentEvents(): Flow<String> = flow {
        // This will be implemented to read from Python stdout/stderr
        // For now, return empty flow
    }
    
    /**
     * Handle agent completion
     */
    suspend fun handleAgentCompletion(): AgentResult = withContext(Dispatchers.IO) {
        // Read final result from StateBridge or Python output
        AgentResult(success = true, message = "Agent completed")
    }
    
    /**
     * Stop running agent
     */
    suspend fun stopAgent() {
        isRunning = false
        currentProcess?.destroy()
        currentProcess = null
    }
    
    private fun copyWrapperScript() {
        try {
            val assets = context.assets
            val wrapperScript = assets.open(PYTHON_SCRIPT_NAME)
            val outputFile = File(getWrapperScriptPath())
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(wrapperScript.readBytes())
            wrapperScript.close()
            Log.d(TAG, "Wrapper script copied to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying wrapper script: ${e.message}", e)
        }
    }
    
    private fun getWrapperScriptPath(): String {
        return File(context.filesDir, PYTHON_SCRIPT_NAME).absolutePath
    }
    
    /**
     * Extract wheel files from assets to a temporary directory
     * @return File pointing to the directory containing wheels, or null if extraction failed
     */
    private fun extractWheelsFromAssets(): File? {
        return try {
            val wheelsDir = File(context.filesDir, "wheels")
            wheelsDir.mkdirs()
            
            val assets = context.assets
            val assetFiles = assets.list("") ?: emptyArray()
            
            // Extract main wheel files (droidrun-*.whl)
            assetFiles.filter { it.endsWith(".whl") && it.startsWith(MAIN_WHEEL_PREFIX) }.forEach { wheelName ->
                val inputStream = assets.open(wheelName)
                val outputFile = File(wheelsDir, wheelName)
                outputFile.writeBytes(inputStream.readBytes())
                inputStream.close()
                Log.d(TAG, "Extracted main wheel: $wheelName")
            }
            
            // Extract dependency wheels from wheels/ subdirectory in assets
            val depWheelsDir = File(wheelsDir, WHEELS_DIR_NAME)
            depWheelsDir.mkdirs()
            
            try {
                val depWheelFiles = assets.list(WHEELS_DIR_NAME) ?: emptyArray()
                depWheelFiles.filter { it.endsWith(".whl") }.forEach { wheelName ->
                    val inputStream = assets.open("$WHEELS_DIR_NAME/$wheelName")
                    val outputFile = File(depWheelsDir, wheelName)
                    outputFile.writeBytes(inputStream.readBytes())
                    inputStream.close()
                    Log.d(TAG, "Extracted dependency wheel: $wheelName")
                }
            } catch (e: Exception) {
                // wheels/ directory might not exist in assets, that's okay
                Log.d(TAG, "No dependency wheels directory in assets: ${e.message}")
            }
            
            wheelsDir
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting wheels from assets: ${e.message}", e)
            null
        }
    }
}

/**
 * Agent execution result
 */
data class AgentResult(
    val success: Boolean,
    val message: String,
    val output: String = ""
)
