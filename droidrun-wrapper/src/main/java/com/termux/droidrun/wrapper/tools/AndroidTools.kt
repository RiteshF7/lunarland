package com.termux.droidrun.wrapper.tools

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.termux.droidrun.portal.DroidrunSDK
import com.termux.droidrun.portal.model.ElementNode
import com.termux.droidrun.portal.model.FormattedDeviceState
import com.termux.droidrun.portal.model.PhoneState
import com.termux.droidrun.portal.util.InteractiveElementFilter
import com.termux.droidrun.portal.util.StateFormatter
import com.termux.droidrun.wrapper.bridge.StateBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.termux.droidrun.portal.callback.DroidrunCallback
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AndroidTools - Native Android implementation of Tools interface
 * 
 * This class replaces AdbTools and uses Portal SDK for direct device control.
 * All device control happens in-process, no ADB required.
 * 
 * State is synchronized with Python via StateBridge (file-based JSON).
 */
class AndroidTools(
    private val context: Context,
    private val stateBridge: StateBridge,
    private val visionEnabled: Boolean = true
) {
    companion object {
        private const val TAG = "AndroidTools"
        private const val STATE_UPDATE_INTERVAL_MS = 100L // 100ms polling
    }
    
    private val sdk: DroidrunSDK = DroidrunSDK.getInstance()
    
    // State management (matching AdbTools interface)
    private val clickableElementsCache = mutableListOf<Map<String, Any>>()
    private val memory = mutableListOf<String>()
    private val reason = AtomicReference<String?>(null)
    private val success = AtomicReference<Boolean?>(null)
    private val finished = AtomicBoolean(false)
    var saveTrajectories: String = "none"
    
    // Context for event streaming (if needed)
    private var _ctx: Any? = null
    
    // LLM instances (for app opener, text manipulator - passed from Python)
    var appOpenerLlm: Any? = null
    var textManipulatorLlm: Any? = null
    var credentialManager: Any? = null
    
    // Tree filter and formatter (matching Python implementation)
    private val treeFilter = ConciseTreeFilter()
    private val treeFormatter = IndexedTreeFormatter()
    
    private var rawTreeCache: List<Map<String, Any>>? = null
    private var filteredTreeCache: List<Map<String, Any>>? = null
    
    /**
     * Set context for event streaming (matches AdbTools._set_context)
     */
    fun setContext(ctx: Any?) {
        _ctx = ctx
    }
    
    /**
     * Get current date on device
     */
    suspend fun getDate(): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault())
        dateFormat.format(Date())
    }
    
    /**
     * Get device state - matches AdbTools.get_state() return format
     * Returns: (formatted_text, focused_text, a11y_tree, phone_state)
     */
    suspend fun getState(): Tuple4<String, String, List<Map<String, Any>>, Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                // Get formatted state from Portal SDK (already a suspend function)
                val formattedState: FormattedDeviceState = sdk.getFormattedState()
                
                // Convert FormattedDeviceState to Python-compatible format
                val formattedElements = formattedState.formattedElements
                val phoneState = formattedState.phoneState
                
                // Build a11y_tree in Python format (list of maps with index, className, text, bounds, etc.)
                val a11yTree = formattedElements.map { element ->
                    mapOf(
                        "index" to element.index,
                        "className" to element.className,
                        "resourceId" to element.resourceId,
                        "text" to element.text,
                        "bounds" to "${element.bounds.left},${element.bounds.top},${element.bounds.right},${element.bounds.bottom}",
                        "type" to when {
                            element.isClickable -> "Clickable"
                            element.isCheckable -> "Checkable"
                            element.isEditable -> "Input"
                            element.isScrollable -> "Container"
                            else -> "View"
                        },
                        "children" to emptyList<Map<String, Any>>() // Can be expanded if needed
                    )
                }
                
                // Build phone_state map
                val phoneStateMap = mapOf(
                    "packageName" to (phoneState.packageName ?: "Unknown"),
                    "currentApp" to (phoneState.appName ?: "Unknown"),
                    "activityName" to (phoneState.activityName ?: "Unknown"),
                    "keyboardVisible" to phoneState.keyboardVisible,
                    "isEditable" to phoneState.isEditable,
                    "focusedElement" to (phoneState.focusedElement?.let { node ->
                        mapOf(
                            "text" to (node.text?.toString() ?: ""),
                            "className" to (node.className?.toString() ?: ""),
                            "packageName" to (node.packageName?.toString() ?: "")
                        )
                    } ?: emptyMap<String, Any>())
                )
                
                // Format text (matching IndexedFormatter format)
                val phoneStateText = formatPhoneState(phoneStateMap)
                val uiElementsText = formatUIElements(a11yTree)
                val formattedText = "$phoneStateText\n\n$uiElementsText"
                
                // Extract focused text
                val focusedText = formattedState.focusedText ?: ""
                
                // Update cache
                clickableElementsCache.clear()
                clickableElementsCache.addAll(a11yTree)
                rawTreeCache = a11yTree
                filteredTreeCache = a11yTree
                
                // Write to StateBridge for Python
                stateBridge.writeDeviceState(formattedText, focusedText, a11yTree, phoneStateMap)

                Log.i(TAG, "📱 AndroidTools: State retrieved")
                Log.d(TAG, "   - UI Elements: ${a11yTree.size}")
                Log.d(TAG, "   - Current App: ${phoneStateMap["currentApp"]}")
                Log.d(TAG, "   - Package: ${phoneStateMap["packageName"]}")
                
                Tuple4(formattedText, focusedText, a11yTree, phoneStateMap)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting state: ${e.message}", e)
                // Return empty state on error
                val emptyPhoneState = mapOf<String, Any>(
                    "packageName" to "Unknown",
                    "currentApp" to "Unknown",
                    "activityName" to "Unknown",
                    "keyboardVisible" to false,
                    "isEditable" to false,
                    "focusedElement" to emptyMap<String, Any>()
                )
                Tuple4("Error: ${e.message}", "", emptyList(), emptyPhoneState)
            }
        }
    }
    
    /**
     * Tap element by index
     */
    suspend fun tapByIndex(index: Int): String = withContext(Dispatchers.Main) {
        try {
            val success = suspendCancellableCoroutine<Boolean> { continuation ->
                sdk.clickElement(index, object : DroidrunCallback<Boolean> {
                    override fun onSuccess(result: Boolean) {
                        continuation.resume(result)
                    }
                    
                    override fun onError(error: String, exception: Exception?) {
                        Log.e(TAG, "Error clicking element: $error", exception)
                        continuation.resume(false)
                    }
                })
            }
            
            val element = findElementByIndex(clickableElementsCache, index)
            val elementText = element?.get("text")?.toString() ?: "No text"
            val elementClass = element?.get("className")?.toString() ?: "Unknown class"
            
            if (success) {
                "Tapped element with index $index | Text: '$elementText' | Class: $elementClass"
            } else {
                "Error: Failed to tap element with index $index"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tapping element: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Extract element coordinates by index (for coordinate-based tapping)
     */
    fun extractElementCoordinatesByIndex(index: Int): Pair<Int, Int> {
        val element = findElementByIndex(clickableElementsCache, index)
            ?: throw IllegalArgumentException("No element found with index $index")
        
        val boundsStr = element["bounds"] as? String
            ?: throw IllegalArgumentException("Element with index $index has no bounds")
        
        val bounds = boundsStr.split(",").map { it.toInt() }
        if (bounds.size != 4) {
            throw IllegalArgumentException("Invalid bounds format: $boundsStr")
        }
        
        val (left, top, right, bottom) = bounds
        val x = (left + right) / 2
        val y = (top + bottom) / 2
        
        return Pair(x, y)
    }
    
    /**
     * Swipe gesture
     */
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int = 300
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            // Use AccessibilityService gesture dispatch
            val service = com.termux.droidrun.portal.DroidrunAccessibilityService.getInstance()
            if (service == null) {
                Log.e(TAG, "AccessibilityService not available for swipe")
                return@withContext false
            }
            
            // Create gesture path
            val path = android.graphics.Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            
            val gestureDescription = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, durationMs.toLong()
                ))
                .build()
            
            suspendCancellableCoroutine { continuation ->
                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        Log.d(TAG, "Swipe gesture completed")
                        continuation.resume(true)
                    }
                    
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        Log.e(TAG, "Swipe gesture cancelled")
                        continuation.resume(false)
                    }
                }
                
                val success = service.dispatchGesture(gestureDescription, callback, null)
                if (!success) {
                    continuation.resume(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe: ${e.message}", e)
            false
        }
    }
    
    /**
     * Drag gesture (long press + move)
     */
    suspend fun drag(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int = 3000
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            val service = com.termux.droidrun.portal.DroidrunAccessibilityService.getInstance()
            if (service == null) {
                Log.e(TAG, "AccessibilityService not available for drag")
                return@withContext false
            }
            
            // Create gesture path with longer duration for drag
            val path = android.graphics.Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            
            val gestureDescription = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, durationMs.toLong()
                ))
                .build()
            
            suspendCancellableCoroutine { continuation ->
                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        Log.d(TAG, "Drag gesture completed")
                        continuation.resume(true)
                    }
                    
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        Log.e(TAG, "Drag gesture cancelled")
                        continuation.resume(false)
                    }
                }
                
                val success = service.dispatchGesture(gestureDescription, callback, null)
                if (!success) {
                    continuation.resume(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing drag: ${e.message}", e)
            false
        }
    }
    
    /**
     * Input text
     */
    suspend fun inputText(text: String, index: Int = -1, clear: Boolean = false): String {
        return withContext(Dispatchers.Main) {
            try {
                if (index != -1) {
                    // Tap on the element first to focus it
                    tapByIndex(index)
                    delay(500) // Wait for focus
                }
                
                val success = suspendCancellableCoroutine<Boolean> { continuation ->
                    sdk.inputText(text, clear, object : DroidrunCallback<Boolean> {
                        override fun onSuccess(result: Boolean) {
                            continuation.resume(result)
                        }
                        
                        override fun onError(error: String, exception: Exception?) {
                            Log.e(TAG, "Error inputting text: $error", exception)
                            continuation.resume(false)
                        }
                    })
                }
                
                if (success) {
                    "Text input completed (clear=$clear): ${text.take(50)}${if (text.length > 50) "..." else ""}"
                } else {
                    "Error: Failed to input text"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inputting text: ${e.message}", e)
                "Error sending text input: ${e.message}"
            }
        }
    }
    
    /**
     * Press back button
     */
    suspend fun back(): String = withContext(Dispatchers.Main) {
        try {
            val success = suspendCancellableCoroutine<Boolean> { continuation ->
                sdk.sendKeyEvent(android.view.KeyEvent.KEYCODE_BACK, object : DroidrunCallback<Boolean> {
                    override fun onSuccess(result: Boolean) {
                        continuation.resume(result)
                    }
                    
                    override fun onError(error: String, exception: Exception?) {
                        Log.e(TAG, "Error pressing back: $error", exception)
                        continuation.resume(false)
                    }
                })
            }
            
            if (success) {
                "Pressed key BACK"
            } else {
                "Error: Failed to press back button"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing back: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Press key by keycode
     */
    suspend fun pressKey(keycode: Int): String = withContext(Dispatchers.Main) {
        try {
            val keyNames = mapOf(
                66 to "ENTER",
                4 to "BACK",
                3 to "HOME",
                67 to "DELETE"
            )
            val keyName = keyNames[keycode] ?: keycode.toString()
            
            val success = suspendCancellableCoroutine<Boolean> { continuation ->
                sdk.sendKeyEvent(keycode, object : DroidrunCallback<Boolean> {
                    override fun onSuccess(result: Boolean) {
                        continuation.resume(result)
                    }
                    
                    override fun onError(error: String, exception: Exception?) {
                        Log.e(TAG, "Error pressing key: $error", exception)
                        continuation.resume(false)
                    }
                })
            }
            
            if (success) {
                "Pressed key $keyName"
            } else {
                "Error: Failed to press key $keyName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing key: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Start app
     */
    suspend fun startApp(packageName: String, activity: String = ""): String {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isNotEmpty()) {
                    // Use explicit activity
                    val intent = android.content.Intent().apply {
                        setClassName(packageName, activity)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    delay(500) // Wait for app to start
                    "App started: $packageName with activity $activity"
                } else {
                    // Use launch intent via SDK
                    val success = suspendCancellableCoroutine<Boolean> { continuation ->
                        sdk.launchApp(packageName, object : DroidrunCallback<Boolean> {
                            override fun onSuccess(result: Boolean) {
                                continuation.resume(result)
                            }
                            
                            override fun onError(error: String, exception: Exception?) {
                                Log.e(TAG, "Error launching app: $error", exception)
                                continuation.resume(false)
                            }
                        })
                    }
                    
                    if (success) {
                        "App started: $packageName"
                    } else {
                        "Error: Failed to start app: $packageName"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting app: ${e.message}", e)
                "Error: ${e.message}"
            }
        }
    }
    
    /**
     * Take screenshot
     */
    suspend fun takeScreenshot(): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        try {
            val screenshotBase64 = suspendCancellableCoroutine<String> { continuation ->
                sdk.takeScreenshot(hideOverlay = true, object : DroidrunCallback<String> {
                    override fun onSuccess(result: String) {
                        continuation.resume(result)
                    }
                    
                    override fun onError(error: String, exception: Exception?) {
                        Log.e(TAG, "Error taking screenshot: $error", exception)
                        continuation.resumeWithException(Exception(error))
                    }
                })
            }
            
            val imageBytes = android.util.Base64.decode(screenshotBase64, android.util.Base64.DEFAULT)
            Pair("PNG", imageBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error taking screenshot: ${e.message}", e)
            throw Exception("Error taking screenshot: ${e.message}")
        }
    }
    
    /**
     * List packages
     */
    suspend fun listPackages(includeSystemApps: Boolean = false): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val packageList = suspendCancellableCoroutine<List<android.content.pm.PackageInfo>> { continuation ->
                    sdk.getInstalledPackages(object : DroidrunCallback<List<android.content.pm.PackageInfo>> {
                        override fun onSuccess(result: List<android.content.pm.PackageInfo>) {
                            continuation.resume(result)
                        }
                        
                        override fun onError(error: String, exception: Exception?) {
                            Log.e(TAG, "Error getting packages: $error", exception)
                            continuation.resume(emptyList())
                        }
                    })
                }
                
                val packages = packageList.filter { pkgInfo ->
                    includeSystemApps || !isSystemApp(pkgInfo)
                }.map { it.packageName }
                
                packages.sorted()
            } catch (e: Exception) {
                Log.e(TAG, "Error listing packages: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get apps
     */
    suspend fun getApps(includeSystem: Boolean = true): List<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val apps = mutableListOf<Map<String, String>>()
                val pm = context.packageManager
                
                val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                
                val resolvedApps = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }
                
                resolvedApps.forEach { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    if (includeSystem || !isSystemPackage(packageName)) {
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            apps.add(mapOf(
                                "package" to packageName,
                                "label" to label
                            ))
                        } catch (e: PackageManager.NameNotFoundException) {
                            // Skip
                        }
                    }
                }
                
                apps.sortedBy { it["label"] }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting apps: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    /**
     * Remember information
     */
    fun remember(information: String): String {
        if (information.isBlank()) {
            return "Error: Please provide valid information to remember."
        }
        
        memory.add(information.trim())
        
        // Limit memory size
        val maxMemoryItems = 10
        if (memory.size > maxMemoryItems) {
            memory.removeAt(0)
        }
        
        return "Remembered: $information"
    }
    
    /**
     * Get memory
     */
    fun getMemory(): List<String> {
        return memory.toList()
    }
    
    /**
     * Complete task
     */
    suspend fun complete(success: Boolean, reason: String = "") {
        this.success.set(success)
        this.reason.set(reason.ifEmpty { 
            if (success) "Task completed successfully." else "Task failed."
        })
        finished.set(true)
    }
    
    // Helper methods
    
    private fun findElementByIndex(elements: List<Map<String, Any>>, targetIndex: Int): Map<String, Any>? {
        for (element in elements) {
            if (element["index"] == targetIndex) {
                return element
            }
            val children = element["children"] as? List<Map<String, Any>>
            if (children != null) {
                val found = findElementByIndex(children, targetIndex)
                if (found != null) return found
            }
        }
        return null
    }
    
    private fun formatPhoneState(phoneState: Map<String, Any>): String {
        val currentApp = phoneState["currentApp"] as? String ?: "Unknown"
        val packageName = phoneState["packageName"] as? String ?: "Unknown"
        val keyboardVisible = phoneState["keyboardVisible"] as? Boolean ?: false
        val focusedElement = phoneState["focusedElement"] as? Map<*, *>
        val focusedText = focusedElement?.get("text") as? String ?: ""
        
        return """**Current Phone State:**
• **App:** $currentApp ($packageName)
• **Keyboard:** ${if (keyboardVisible) "Visible" else "Hidden"}
• **Focused Element:** '$focusedText'"""
    }
    
    private fun formatUIElements(a11yTree: List<Map<String, Any>>): String {
        if (a11yTree.isEmpty()) {
            return "Current Clickable UI elements from the device in the schema 'index. className: resourceId, text - bounds(x1,y1,x2,y2)':\nNo UI elements found"
        }
        
        val formatted = a11yTree.joinToString("\n") { element ->
            val index = element["index"] ?: ""
            val className = element["className"] as? String ?: ""
            val resourceId = element["resourceId"] as? String ?: ""
            val text = element["text"] as? String ?: ""
            val bounds = element["bounds"] as? String ?: ""
            
            val parts = mutableListOf<String>()
            if (index != "") parts.add("$index.")
            if (className.isNotEmpty()) parts.add("$className:")
            
            val details = mutableListOf<String>()
            if (resourceId.isNotEmpty()) details.add("\"$resourceId\"")
            if (text.isNotEmpty()) details.add("\"$text\"")
            if (details.isNotEmpty()) parts.add(details.joinToString(", "))
            if (bounds.isNotEmpty()) parts.add("- ($bounds)")
            
            parts.joinToString(" ")
        }
        
        return "Current Clickable UI elements from the device in the schema 'index. className: resourceId, text - bounds(x1,y1,x2,y2)':\n$formatted"
    }
    
    private fun isSystemApp(pkgInfo: android.content.pm.PackageInfo): Boolean {
        return (pkgInfo.applicationInfo?.flags ?: 0 and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }
    
    private fun isSystemPackage(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    // Simple tree filter (concise filter logic)
    private class ConciseTreeFilter {
        // Filtering is done by Portal SDK's InteractiveElementFilter
        // This is just a placeholder for compatibility
    }
    
    // Simple tree formatter (indexed formatter logic)
    private class IndexedTreeFormatter {
        // Formatting is done in formatUIElements method
        // This is just a placeholder for compatibility
    }
    
    // Helper data class for tuple return
    data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}


