package com.termux.droidrun.wrapper.tools

import android.content.Context
import android.util.Log
import com.termux.droidrun.wrapper.bridge.StateBridge
import com.termux.droidrun.wrapper.bridge.PythonAction
import com.termux.droidrun.wrapper.bridge.ActionResult
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.json.JSONObject

/**
 * ActionHandler - Processes actions from Python and executes them via AndroidTools
 * 
 * This runs in a background coroutine and continuously polls for actions
 * from the StateBridge, executing them and writing results back.
 */
class ActionHandler(
    private val context: Context,
    private val androidTools: AndroidTools,
    private val stateBridge: StateBridge
) {
    companion object {
        private const val TAG = "ActionHandler"
    }
    
    private var isRunning = false
    private var handlerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start the action handler loop
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "ActionHandler already running")
            return
        }
        
        isRunning = true
        handlerJob = scope.launch {
            Log.d(TAG, "ActionHandler started")
            
            while (isRunning) {
                try {
                    // Poll for actions from Python
                    val action = stateBridge.readPythonAction()
                    
                        if (action != null) {
                            Log.i(TAG, "⚡ ActionHandler: Processing action")
                            Log.d(TAG, "   - Type: ${action.actionType}")
                            Log.d(TAG, "   - Parameters: ${action.parameters}")
                            val result = executeAction(action)
                            Log.i(TAG, "✅ ActionHandler: Action completed")
                            Log.d(TAG, "   - Success: ${result.success}")
                            Log.d(TAG, "   - Result: ${result.result.take(100)}")

                            // Write result back
                            stateBridge.writeActionResult(result)
                        } else {
                            // No action, wait a bit before polling again
                            delay(100)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in action handler loop: ${e.message}", e)
                    delay(1000) // Wait longer on error
                }
            }
            
            Log.d(TAG, "ActionHandler stopped")
        }
    }
    
    /**
     * Stop the action handler
     */
    fun stop() {
        isRunning = false
        handlerJob?.cancel()
        handlerJob = null
    }
    
    /**
     * Execute an action from Python
     */
    private suspend fun executeAction(action: PythonAction): ActionResult {
        return try {
            val result = when (action.actionType) {
                "tap_by_index" -> {
                    val index = action.parameters["index"]?.toIntOrNull() ?: -1
                    if (index >= 0) {
                        androidTools.tapByIndex(index)
                    } else {
                        "Error: Invalid index"
                    }
                }
                
                "swipe" -> {
                    val startX = action.parameters["start_x"]?.toIntOrNull() ?: 0
                    val startY = action.parameters["start_y"]?.toIntOrNull() ?: 0
                    val endX = action.parameters["end_x"]?.toIntOrNull() ?: 0
                    val endY = action.parameters["end_y"]?.toIntOrNull() ?: 0
                    val durationMs = action.parameters["duration_ms"]?.toIntOrNull() ?: 300
                    
                    val success = androidTools.swipe(startX, startY, endX, endY, durationMs)
                    if (success) "Swipe completed" else "Swipe failed"
                }
                
                "drag" -> {
                    val startX = action.parameters["start_x"]?.toIntOrNull() ?: 0
                    val startY = action.parameters["start_y"]?.toIntOrNull() ?: 0
                    val endX = action.parameters["end_x"]?.toIntOrNull() ?: 0
                    val endY = action.parameters["end_y"]?.toIntOrNull() ?: 0
                    val durationMs = action.parameters["duration_ms"]?.toIntOrNull() ?: 3000
                    
                    val success = androidTools.drag(startX, startY, endX, endY, durationMs)
                    if (success) "Drag completed" else "Drag failed"
                }
                
                "input_text" -> {
                    val text = action.parameters["text"] ?: ""
                    val index = action.parameters["index"]?.toIntOrNull() ?: -1
                    val clear = action.parameters["clear"]?.toBoolean() ?: false
                    
                    androidTools.inputText(text, index, clear)
                }
                
                "back" -> {
                    androidTools.back()
                }
                
                "press_key" -> {
                    val keycode = action.parameters["keycode"]?.toIntOrNull() ?: -1
                    if (keycode >= 0) {
                        androidTools.pressKey(keycode)
                    } else {
                        "Error: Invalid keycode"
                    }
                }
                
                "start_app" -> {
                    val packageName = action.parameters["package"] ?: ""
                    val activity = action.parameters["activity"] ?: ""
                    
                    if (packageName.isNotEmpty()) {
                        androidTools.startApp(packageName, activity)
                    } else {
                        "Error: Package name required"
                    }
                }
                
                "take_screenshot" -> {
                    val (format, imageBytes) = androidTools.takeScreenshot()
                    val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                    JSONObject().apply {
                        put("format", format)
                        put("image_base64", base64)
                    }.toString()
                }
                
                "list_packages" -> {
                    val includeSystem = action.parameters["include_system_apps"]?.toBoolean() ?: false
                    val packages = androidTools.listPackages(includeSystem)
                    JSONObject().apply {
                        put("packages", org.json.JSONArray(packages))
                    }.toString()
                }
                
                "get_apps" -> {
                    val includeSystem = action.parameters["include_system"]?.toBoolean() ?: true
                    val apps = androidTools.getApps(includeSystem)
                    JSONObject().apply {
                        val appsArray = org.json.JSONArray()
                        apps.forEach { app ->
                            appsArray.put(org.json.JSONObject(app))
                        }
                        put("apps", appsArray)
                    }.toString()
                }
                
                "complete" -> {
                    val success = action.parameters["success"]?.toBoolean() ?: false
                    val reason = action.parameters["reason"] ?: ""
                    androidTools.complete(success, reason)
                    "Task completed"
                }
                
                else -> {
                    "Error: Unknown action type: ${action.actionType}"
                }
            }
            
            ActionResult(
                actionId = action.actionId,
                success = true,
                result = result,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action ${action.actionType}: ${e.message}", e)
            ActionResult(
                actionId = action.actionId,
                success = false,
                result = "",
                error = e.message,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

