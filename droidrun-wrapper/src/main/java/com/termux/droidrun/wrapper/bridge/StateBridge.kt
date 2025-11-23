package com.termux.droidrun.wrapper.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * StateBridge - JSON file-based communication between Kotlin and Python
 * 
 * This class manages state synchronization between Android native code and Python agent.
 * Uses file-based communication for stability and simplicity.
 */
class StateBridge(private val context: Context) {
    companion object {
        private const val TAG = "StateBridge"
        private const val DEVICE_STATE_FILE = "device_state.json"
        private const val PYTHON_ACTIONS_FILE = "python_actions.json"
        private const val ACTION_RESULT_FILE = "action_result.json"
    }
    
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }
    
    private val lock = ReentrantReadWriteLock()
    
    private val filesDir: File = context.filesDir
    private val deviceStateFile: File = File(filesDir, DEVICE_STATE_FILE)
    private val pythonActionsFile: File = File(filesDir, PYTHON_ACTIONS_FILE)
    private val actionResultFile: File = File(filesDir, ACTION_RESULT_FILE)
    
    /**
     * Write device state to file (for Python to read)
     * Format matches AdbTools.get_state() return: (formatted_text, focused_text, a11y_tree, phone_state)
     */
        suspend fun writeDeviceState(
            formattedText: String,
            focusedText: String,
            a11yTree: List<Map<String, Any>>,
            phoneState: Map<String, Any>
        ): Boolean = withContext(Dispatchers.IO) {
            lock.write {
                try {
                    // Convert Maps to JSON strings for serialization
                    val a11yTreeJson = org.json.JSONArray(a11yTree.map { org.json.JSONObject(it) }).toString()
                    val phoneStateJson = org.json.JSONObject(phoneState).toString()

                    val state = DeviceState(
                        formattedText = formattedText,
                        focusedText = focusedText,
                        a11yTreeJson = a11yTreeJson,
                        phoneStateJson = phoneStateJson,
                        timestamp = System.currentTimeMillis()
                    )

                    val jsonString = json.encodeToString(state)
                    deviceStateFile.writeText(jsonString)

                    Log.i(TAG, "📤 StateBridge: Device state written")
                    Log.d(TAG, "   - Elements: ${a11yTree.size}")
                    Log.d(TAG, "   - Focused text: ${focusedText.take(50)}")
                    Log.d(TAG, "   - File: ${deviceStateFile.absolutePath}")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error writing device state: ${e.message}", e)
                    false
                }
            }
        }
    
    /**
     * Read device state from file (Python can check if updated)
     */
    suspend fun readDeviceState(): DeviceState? = withContext(Dispatchers.IO) {
        lock.read {
            try {
                if (!deviceStateFile.exists()) {
                    return@withContext null
                }
                
                val jsonString = deviceStateFile.readText()
                json.decodeFromString<DeviceState>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading device state: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Read pending action from Python (non-blocking)
     */
        suspend fun readPythonAction(): PythonAction? = withContext(Dispatchers.IO) {
            lock.write {
                try {
                    if (!pythonActionsFile.exists()) {
                        return@withContext null
                    }

                    val jsonString = pythonActionsFile.readText()
                    val action = json.decodeFromString<PythonAction>(jsonString)

                    Log.i(TAG, "📥 StateBridge: Python action received")
                    Log.d(TAG, "   - Type: ${action.actionType}")
                    Log.d(TAG, "   - Parameters: ${action.parameters}")
                    Log.d(TAG, "   - Action ID: ${action.actionId}")

                    // Delete file after reading (acknowledge receipt)
                    pythonActionsFile.delete()

                    action
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error reading Python action: ${e.message}", e)
                    null
                }
            }
        }
    
    /**
     * Write action result back to Python
     */
        suspend fun writeActionResult(result: ActionResult): Boolean = withContext(Dispatchers.IO) {
            lock.write {
                try {
                    val jsonString = json.encodeToString(result)
                    actionResultFile.writeText(jsonString)

                    Log.i(TAG, "📤 StateBridge: Action result written")
                    Log.d(TAG, "   - Action ID: ${result.actionId}")
                    Log.d(TAG, "   - Success: ${result.success}")
                    Log.d(TAG, "   - Result: ${result.result.take(100)}")
                    if (result.error != null) {
                        Log.w(TAG, "   - Error: ${result.error}")
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error writing action result: ${e.message}", e)
                    false
                }
            }
        }
    
    /**
     * Read action result (Python can check if action completed)
     */
    suspend fun readActionResult(): ActionResult? = withContext(Dispatchers.IO) {
        lock.read {
            try {
                if (!actionResultFile.exists()) {
                    return@withContext null
                }
                
                val jsonString = actionResultFile.readText()
                val result = json.decodeFromString<ActionResult>(jsonString)
                
                // Delete after reading
                actionResultFile.delete()
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error reading action result: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Get the file path for Python to use
     */
    fun getDeviceStatePath(): String = deviceStateFile.absolutePath
    
    fun getPythonActionsPath(): String = pythonActionsFile.absolutePath
    
    fun getActionResultPath(): String = actionResultFile.absolutePath
    
    /**
     * Clear all state files
     */
    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        lock.write {
            deviceStateFile.delete()
            pythonActionsFile.delete()
            actionResultFile.delete()
        }
    }
}

/**
 * Device state structure matching Python's get_state() return format
 */
@Serializable
data class DeviceState(
    val formattedText: String,
    val focusedText: String,
    val a11yTreeJson: String,  // JSON string instead of Map for serialization
    val phoneStateJson: String,  // JSON string instead of Map for serialization
    val timestamp: Long
)

/**
 * Action from Python to execute
 */
@Serializable
data class PythonAction(
    val actionType: String,
    val parameters: Map<String, String>,
    val timestamp: Long,
    val actionId: String
)

/**
 * Result of action execution
 */
@Serializable
data class ActionResult(
    val actionId: String,
    val success: Boolean,
    val result: String,
    val error: String? = null,
    val timestamp: Long
)

