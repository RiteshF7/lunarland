package com.termux.droidrun.portal

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.termux.droidrun.portal.callback.DroidrunCallback
import com.termux.droidrun.portal.model.ElementNode
import com.termux.droidrun.portal.model.FormattedDeviceState
import com.termux.droidrun.portal.model.PhoneState
import com.termux.droidrun.portal.util.InteractiveElementFilter
import com.termux.droidrun.portal.util.StateCacheManager
import com.termux.droidrun.portal.util.StateFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Main SDK entry point for Droidrun Portal
 * Singleton pattern with initialization and lifecycle management
 */
class DroidrunSDK private constructor() {
    
    companion object {
        private const val TAG = "DroidrunSDK"
        
        @Volatile
        private var INSTANCE: DroidrunSDK? = null
        
        /**
         * Get the singleton instance of DroidrunSDK
         * @throws IllegalStateException if SDK has not been initialized
         */
        fun getInstance(): DroidrunSDK {
            return INSTANCE ?: throw IllegalStateException(
                "DroidrunSDK has not been initialized. Call DroidrunSDK.initialize(context) first."
            )
        }
        
        /**
         * Initialize the SDK with application context
         * Should be called in Application.onCreate() or early in app lifecycle
         * @param context Application context
         */
        fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = DroidrunSDK().apply {
                            this.context = context.applicationContext
                            setupLifecycleCallbacks()
                        }
                        Log.d(TAG, "DroidrunSDK initialized")
                    }
                }
            }
        }
    }
    
    private var context: Context? = null
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
    private val stateCacheManager = StateCacheManager(maxSize = 5)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * Check if the accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val context = this.context ?: return false
        val accessibilityServiceName = "${context.packageName}/${DroidrunAccessibilityService::class.java.canonicalName}"
        
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabledServices?.contains(accessibilityServiceName) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility status: ${e.message}")
            false
        }
    }
    
    /**
     * Check if the keyboard IME is enabled
     */
    fun isKeyboardIMEEnabled(): Boolean {
        val context = this.context ?: return false
        
        // First check if the IME instance is available (most reliable)
        if (DroidrunKeyboardIME.isAvailable()) {
            Log.d(TAG, "Keyboard IME instance is available")
            return true
        }
        
        // Fallback: Check if it's enabled in settings
        return try {
            val packageName = context.packageName
            val className = DroidrunKeyboardIME::class.java.canonicalName
            val simpleClassName = DroidrunKeyboardIME::class.java.simpleName
            
            // Try different IME ID formats
            val imeId1 = "$packageName/$className"
            val imeId2 = "$packageName/.$simpleClassName"
            val imeId3 = "$packageName/$simpleClassName"
            
            val enabledIMEs = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: ""
            
            Log.d(TAG, "Checking IME status. Package: $packageName, Class: $className")
            Log.d(TAG, "Enabled IMEs: $enabledIMEs")
            Log.d(TAG, "Looking for: $imeId1, $imeId2, $imeId3, or $simpleClassName")
            
            // Check multiple formats
            val enabled = enabledIMEs.contains(imeId1) || 
                        enabledIMEs.contains(imeId2) ||
                        enabledIMEs.contains(imeId3) ||
                        enabledIMEs.contains(className) ||
                        enabledIMEs.contains(simpleClassName) ||
                        enabledIMEs.contains("DroidrunKeyboardIME") ||
                        enabledIMEs.contains("Droidrun Keyboard")
            
            if (enabled) {
                Log.d(TAG, "IME is enabled in settings (instance not available yet - will activate when input is focused)")
            } else {
                Log.w(TAG, "IME not found in enabled IMEs list")
            }
            
            enabled
        } catch (e: SecurityException) {
            // On Android 14+ (targetSdk 34+), we can't read ENABLED_INPUT_METHODS
            // Fall back to checking if instance is available (will return false if not)
            Log.w(TAG, "Cannot read enabled IMEs due to SecurityException (targetSdk 34+). " +
                    "This is expected. The IME will work when an input field is focused.")
            // Since we already checked instance availability at the start,
            // and that returned false, we return false here too
            // But don't log as error - this is expected behavior on newer Android versions
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard IME status: ${e.message}", e)
            false
        }
    }
    
    /**
     * Open accessibility settings to enable the service
     */
    fun openAccessibilitySettings() {
        val context = this.context ?: return
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}")
        }
    }
    
    /**
     * Open input method settings to enable the keyboard
     */
    fun openKeyboardSettings() {
        val context = this.context ?: return
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening keyboard settings: ${e.message}")
        }
    }
    
    /**
     * Get the accessibility tree (visible elements only)
     */
    fun getAccessibilityTree(callback: DroidrunCallback<List<ElementNode>>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val elements = service.getVisibleElements()
                callback.onSuccess(elements)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting accessibility tree: ${e.message}", e)
                callback.onError("Failed to get accessibility tree: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get the full accessibility tree as JSON
     */
    fun getAccessibilityTreeFull(callback: DroidrunCallback<JSONObject>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val rootNode = service.rootInActiveWindow
                    ?: throw IllegalStateException("No active window available")
                
                val fullTree = AccessibilityTreeBuilder.buildFullAccessibilityTreeJson(rootNode)
                callback.onSuccess(fullTree)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting full accessibility tree: ${e.message}", e)
                callback.onError("Failed to get full accessibility tree: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get the current phone state
     */
    fun getPhoneState(callback: DroidrunCallback<PhoneState>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val phoneState = service.getPhoneState()
                callback.onSuccess(phoneState)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting phone state: ${e.message}", e)
                callback.onError("Failed to get phone state: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get combined state (accessibility tree + phone state)
     */
    fun getCombinedState(callback: DroidrunCallback<CombinedState>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val elements = service.getVisibleElements()
                val phoneState = service.getPhoneState()
                
                val combinedState = CombinedState(elements, phoneState)
                callback.onSuccess(combinedState)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting combined state: ${e.message}", e)
                callback.onError("Failed to get combined state: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get full combined state (full tree + phone state + device context)
     */
    fun getCombinedStateFull(callback: DroidrunCallback<CombinedStateFull>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val rootNode = service.rootInActiveWindow
                    ?: throw IllegalStateException("No active window available")
                
                val fullTree = AccessibilityTreeBuilder.buildFullAccessibilityTreeJson(rootNode)
                val phoneState = service.getPhoneState()
                val deviceContext = service.getDeviceContext()
                
                val combinedState = CombinedStateFull(fullTree, phoneState, deviceContext)
                callback.onSuccess(combinedState)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting full combined state: ${e.message}", e)
                callback.onError("Failed to get full combined state: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get filtered interactive elements (synchronous)
     * Returns only elements that are clickable, checkable, editable, scrollable, or focusable
     * 
     * @return List of filtered interactive ElementNode
     */
    fun getFilteredInteractiveElements(): List<ElementNode> {
        return try {
            val service = DroidrunAccessibilityService.getInstance()
                ?: return emptyList()
            
            val elements = service.getVisibleElements()
            InteractiveElementFilter.filterInteractiveElements(elements)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting filtered interactive elements: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get formatted state optimized for agent consumption (coroutine)
     * This method:
     * 1. Checks cache first
     * 2. If cache miss: gets state, filters, formats, caches, and returns
     * 3. If cache hit: returns cached value
     * 
     * @return FormattedDeviceState with filtered interactive elements and formatted data
     */
    suspend fun getFormattedState(): FormattedDeviceState {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cacheKey = stateCacheManager.getDefaultKey()
                val cachedState = stateCacheManager.get(cacheKey)
                
                if (cachedState != null) {
                    Log.d(TAG, "Returning cached formatted state")
                    return@withContext cachedState
                }
                
                // Cache miss - get fresh state
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                // Get all visible elements
                val elements = service.getVisibleElements()
                
                // Filter to interactive elements only
                val filteredElements = InteractiveElementFilter.filterInteractiveElements(elements)
                
                // Get phone state
                val phoneState = service.getPhoneState()
                
                // Format the state
                val formattedState = StateFormatter.formatDeviceState(filteredElements, phoneState)
                
                // Cache the formatted state
                stateCacheManager.put(cacheKey, formattedState)
                
                Log.d(TAG, "Formatted state with ${formattedState.formattedElements.size} interactive elements")
                formattedState
            } catch (e: Exception) {
                Log.e(TAG, "Error getting formatted state: ${e.message}", e)
                // Return empty state on error
                val emptyPhoneState = PhoneState(
                    focusedElement = null,
                    keyboardVisible = false,
                    packageName = null,
                    appName = null,
                    isEditable = false,
                    activityName = null
                )
                FormattedDeviceState(
                    formattedElements = emptyList(),
                    phoneState = emptyPhoneState,
                    focusedText = null,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
    
    /**
     * Get filtered interactive elements (coroutine)
     * Returns only elements that are clickable, checkable, editable, scrollable, or focusable
     * 
     * @return List of filtered interactive ElementNode
     */
    suspend fun getFilteredState(): List<ElementNode> {
        return withContext(Dispatchers.IO) {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: return@withContext emptyList()
                
                val elements = service.getVisibleElements()
                InteractiveElementFilter.filterInteractiveElements(elements)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting filtered state: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    /**
     * Take a screenshot and return as base64 string
     * @param hideOverlay Whether to hide the overlay during screenshot (default: true)
     */
    fun takeScreenshot(hideOverlay: Boolean = true, callback: DroidrunCallback<String>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val screenshotFuture = service.takeScreenshotBase64(hideOverlay)
                val screenshotBase64 = screenshotFuture.get(5, java.util.concurrent.TimeUnit.SECONDS)
                
                if (screenshotBase64.startsWith("error:")) {
                    callback.onError(screenshotBase64.substring(7), null)
                } else {
                    callback.onSuccess(screenshotBase64)
                }
            } catch (e: java.util.concurrent.TimeoutException) {
                Log.e(TAG, "Screenshot timeout", e)
                callback.onError("Screenshot timeout - operation took too long", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error taking screenshot: ${e.message}", e)
                callback.onError("Failed to take screenshot: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get list of installed packages
     */
    fun getInstalledPackages(callback: DroidrunCallback<List<PackageInfo>>) {
        executorService.execute {
            try {
                val context = this.context ?: throw IllegalStateException("SDK not initialized")
                val pm = context.packageManager
                
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                val resolvedApps: List<android.content.pm.ResolveInfo> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }
                
                val packages = mutableListOf<PackageInfo>()
                for (resolveInfo in resolvedApps) {
                    try {
                        val pkgInfo = pm.getPackageInfo(resolveInfo.activityInfo.packageName, 0)
                        packages.add(pkgInfo)
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Skip packages that can't be found
                    }
                }
                
                callback.onSuccess(packages)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting installed packages: ${e.message}", e)
                callback.onError("Failed to get installed packages: ${e.message}", e)
            }
        }
    }
    
    /**
     * Input text using the keyboard IME
     * @param text Text to input (will be base64 encoded)
     * @param clear Whether to clear existing text first (default: true)
     */
    fun inputText(text: String, clear: Boolean = true, callback: DroidrunCallback<Boolean>) {
        executorService.execute {
            try {
                val keyboardIME = DroidrunKeyboardIME.getInstance()
                    ?: throw IllegalStateException("Keyboard IME not available")
                
                if (!keyboardIME.hasInputConnection()) {
                    throw IllegalStateException("No input connection available - keyboard may not be focused on an input field")
                }
                
                val base64Text = android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                val success = keyboardIME.inputB64Text(base64Text, clear)
                
                if (success) {
                    callback.onSuccess(true)
                } else {
                    callback.onError("Failed to input text via keyboard", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inputting text: ${e.message}", e)
                callback.onError("Failed to input text: ${e.message}", e)
            }
        }
    }
    
    /**
     * Clear text in the current input field
     */
    fun clearText(callback: DroidrunCallback<Boolean>) {
        executorService.execute {
            try {
                val keyboardIME = DroidrunKeyboardIME.getInstance()
                    ?: throw IllegalStateException("Keyboard IME not available")
                
                if (!keyboardIME.hasInputConnection()) {
                    throw IllegalStateException("No input connection available - keyboard may not be focused on an input field")
                }
                
                val success = keyboardIME.clearText()
                
                if (success) {
                    callback.onSuccess(true)
                } else {
                    callback.onError("Failed to clear text via keyboard", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing text: ${e.message}", e)
                callback.onError("Failed to clear text: ${e.message}", e)
            }
        }
    }
    
    /**
     * Send a key event
     * @param keyCode Key code to send (e.g., KeyEvent.KEYCODE_ENTER)
     */
    fun sendKeyEvent(keyCode: Int, callback: DroidrunCallback<Boolean>) {
        executorService.execute {
            try {
                val keyboardIME = DroidrunKeyboardIME.getInstance()
                    ?: throw IllegalStateException("Keyboard IME not available")
                
                if (!keyboardIME.hasInputConnection()) {
                    throw IllegalStateException("No input connection available - keyboard may not be focused on an input field")
                }
                
                val success = keyboardIME.sendKeyEventDirect(keyCode)
                
                if (success) {
                    callback.onSuccess(true)
                } else {
                    callback.onError("Failed to send key event via keyboard", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending key event: ${e.message}", e)
                callback.onError("Failed to send key event: ${e.message}", e)
            }
        }
    }
    
    /**
     * Click on an element by its overlay index
     * @param overlayIndex The index number shown in the overlay
     */
    fun clickElement(overlayIndex: Int, callback: DroidrunCallback<Boolean>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val success = service.clickElementByIndex(overlayIndex)
                
                if (success) {
                    callback.onSuccess(true)
                } else {
                    callback.onError("Failed to click element with index $overlayIndex. Element may not be clickable or not found.", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clicking element: ${e.message}", e)
                callback.onError("Failed to click element: ${e.message}", e)
            }
        }
    }
    
    /**
     * Set overlay visibility
     * @param visible Whether overlay should be visible
     */
    fun setOverlayVisible(visible: Boolean) {
        val service = DroidrunAccessibilityService.getInstance()
        service?.setOverlayVisible(visible)
    }
    
    /**
     * Set overlay offset
     * @param offset Vertical offset in pixels (negative values shift upward)
     */
    fun setOverlayOffset(offset: Int) {
        val service = DroidrunAccessibilityService.getInstance()
        service?.setOverlayOffset(offset)
    }
    
    /**
     * Get overlay offset
     */
    fun getOverlayOffset(): Int {
        val service = DroidrunAccessibilityService.getInstance()
        return service?.getOverlayOffset() ?: 0
    }
    
    /**
     * Check if overlay is visible
     */
    fun isOverlayVisible(): Boolean {
        val service = DroidrunAccessibilityService.getInstance()
        return service?.isOverlayVisible() ?: false
    }
    
    /**
     * Get SDK version
     */
    fun getVersion(): String {
        val context = this.context ?: return "Unknown"
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting version: ${e.message}")
            "Unknown"
        }
    }
    
    /**
     * Launch an app by package name
     * @param packageName Package name of the app to launch
     */
    fun launchApp(packageName: String, callback: DroidrunCallback<Boolean>) {
        executorService.execute {
            try {
                val context = this.context ?: throw IllegalStateException("SDK not initialized")
                val pm = context.packageManager
                
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                    ?: throw IllegalStateException("App with package $packageName not found or cannot be launched")
                
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                
                Log.d(TAG, "Launched app: $packageName")
                callback.onSuccess(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error launching app: ${e.message}", e)
                callback.onError("Failed to launch app: ${e.message}", e)
            }
        }
    }
    
    /**
     * Launch a URL in Chrome
     * @param url URL to open
     */
    fun launchUrlInChrome(url: String, callback: DroidrunCallback<Boolean>) {
        executorService.execute {
            try {
                val context = this.context ?: throw IllegalStateException("SDK not initialized")
                
                // Try to launch Chrome with the URL
                val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.android.chrome")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    context.startActivity(chromeIntent)
                    Log.d(TAG, "Opened URL in Chrome: $url")
                    callback.onSuccess(true)
                } catch (e: Exception) {
                    // Fallback to default browser
                    val defaultIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(defaultIntent)
                    Log.d(TAG, "Opened URL in default browser: $url")
                    callback.onSuccess(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening URL: ${e.message}", e)
                callback.onError("Failed to open URL: ${e.message}", e)
            }
        }
    }
    
    /**
     * Find an element by text (searches recursively)
     * @param searchText Text to search for
     */
    fun findElementByText(searchText: String, callback: DroidrunCallback<ElementNode?>) {
        executorService.execute {
            try {
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                val elements = service.getVisibleElements()
                
                // Search recursively through all elements
                var foundElement: ElementNode? = null
                for (rootElement in elements) {
                    foundElement = findElementByTextRecursive(rootElement, searchText)
                    if (foundElement != null) break
                }
                
                callback.onSuccess(foundElement)
            } catch (e: Exception) {
                Log.e(TAG, "Error finding element: ${e.message}", e)
                callback.onError("Failed to find element: ${e.message}", e)
            }
        }
    }
    
    private fun findElementByTextRecursive(element: ElementNode, searchText: String): ElementNode? {
        if (element.text.contains(searchText, ignoreCase = true)) {
            return element
        }
        for (child in element.children) {
            val found = findElementByTextRecursive(child, searchText)
            if (found != null) return found
        }
        return null
    }
    
    /**
     * Wait for an element to appear by text (with timeout)
     * @param searchText Text to search for
     * @param timeoutSeconds Timeout in seconds (default: 10)
     */
    fun waitForElementByText(searchText: String, timeoutSeconds: Int = 10, callback: DroidrunCallback<ElementNode?>) {
        executorService.execute {
            val startTime = System.currentTimeMillis()
            val timeoutMs = timeoutSeconds * 1000L
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val service = DroidrunAccessibilityService.getInstance()
                        ?: throw IllegalStateException("Accessibility service not available")
                    
                    val elements = service.getVisibleElements()
                    
                    var foundElement: ElementNode? = null
                    for (rootElement in elements) {
                        foundElement = findElementByTextRecursive(rootElement, searchText)
                        if (foundElement != null) break
                    }
                    
                    if (foundElement != null) {
                        callback.onSuccess(foundElement)
                        return@execute
                    }
                    
                    // Wait a bit before retrying
                    Thread.sleep(500)
                } catch (e: Exception) {
                    Log.e(TAG, "Error waiting for element: ${e.message}", e)
                    callback.onError("Failed to wait for element: ${e.message}", e)
                    return@execute
                }
            }
            
            callback.onError("Timeout: Element with text '$searchText' not found within $timeoutSeconds seconds", null)
        }
    }
    
    /**
     * Complete a task: Open Chrome, navigate to Google, search for a term
     * @param searchTerm Term to search for
     */
    fun completeGoogleSearchTask(searchTerm: String, callback: DroidrunCallback<String>) {
        executorService.execute {
            try {
                val context = this.context ?: throw IllegalStateException("SDK not initialized")
                val service = DroidrunAccessibilityService.getInstance()
                    ?: throw IllegalStateException("Accessibility service not available")
                
                Log.d(TAG, "Starting Google search task for: $searchTerm")
                
                // Step 1: Launch Chrome with Google.com
                val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    setPackage("com.android.chrome")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    context.startActivity(chromeIntent)
                    Log.d(TAG, "Launched Chrome with Google.com")
                } catch (e: Exception) {
                    // Fallback to default browser
                    val defaultIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(defaultIntent)
                    Log.d(TAG, "Launched default browser with Google.com")
                }
                
                // Step 2: Wait for page to load (wait for search input)
                Thread.sleep(3000) // Give page time to load
                
                // Step 3: Find the search input field
                var searchInput: ElementNode? = null
                var attempts = 0
                val maxAttempts = 20 // 10 seconds total
                
                while (searchInput == null && attempts < maxAttempts) {
                    val elements = service.getVisibleElements()
                    
                    // Look for search input - try multiple strategies:
                    // 1. Look for editable elements
                    // 2. Look for elements with "Search" or "search" text
                    // 3. Look for elements with "q" name (Google's search input name)
                    for (rootElement in elements) {
                        // First try to find by text
                        searchInput = findElementByTextRecursive(rootElement, "Search")
                        if (searchInput != null && searchInput.nodeInfo.isEditable) break
                        
                        // Try finding editable element
                        searchInput = findEditableElement(rootElement)
                        if (searchInput != null) {
                            // Prefer elements that are larger (likely the main search bar)
                            val rect = searchInput.rect
                            if (rect.width() > 200 && rect.height() > 30) {
                                break
                            }
                        }
                    }
                    
                    if (searchInput == null) {
                        Thread.sleep(500)
                        attempts++
                        Log.d(TAG, "Waiting for search input... attempt $attempts/$maxAttempts")
                    }
                }
                
                if (searchInput == null) {
                    callback.onError("Could not find search input field after $maxAttempts attempts", null)
                    return@execute
                }
                
                Log.d(TAG, "Found search input at index: ${searchInput.overlayIndex}, text: '${searchInput.text}', className: ${searchInput.className}")
                
                // Step 4: Click on the search input to focus it
                val clickSuccess = service.clickElementByIndex(searchInput.overlayIndex)
                if (!clickSuccess) {
                    callback.onError("Failed to click on search input", null)
                    return@execute
                }
                
                Log.d(TAG, "Clicked on search input")
                Thread.sleep(1500) // Wait for keyboard to appear and input to be focused
                
                // Step 5: Wait for keyboard IME to become available (it activates when input is focused)
                var keyboardIME: DroidrunKeyboardIME? = null
                var imeAttempts = 0
                val maxImeAttempts = 10 // 5 seconds
                
                while (keyboardIME == null && imeAttempts < maxImeAttempts) {
                    keyboardIME = DroidrunKeyboardIME.getInstance()
                    if (keyboardIME == null) {
                        Thread.sleep(500)
                        imeAttempts++
                        Log.d(TAG, "Waiting for keyboard IME to become available... attempt $imeAttempts/$maxImeAttempts")
                    }
                }
                
                if (keyboardIME == null) {
                    callback.onError("Keyboard IME not available. Please ensure Droidrun Keyboard is enabled and selected as your input method.", null)
                    return@execute
                }
                
                if (!keyboardIME.hasInputConnection()) {
                    callback.onError("No input connection available. The input field may not be properly focused.", null)
                    return@execute
                }
                
                // Clear and input text
                keyboardIME.clearText()
                Thread.sleep(300)
                val inputSuccess = keyboardIME.inputText(searchTerm, clear = false)
                
                if (!inputSuccess) {
                    callback.onError("Failed to input search term", null)
                    return@execute
                }
                
                Log.d(TAG, "Input search term: $searchTerm")
                Thread.sleep(500)
                
                // Step 6: Press Enter key
                val enterSuccess = keyboardIME.sendKeyEventDirect(KeyEvent.KEYCODE_ENTER)
                
                if (!enterSuccess) {
                    callback.onError("Failed to press Enter key", null)
                    return@execute
                }
                
                Log.d(TAG, "Pressed Enter key")
                callback.onSuccess("Task completed successfully! Searched for: $searchTerm")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error completing task: ${e.message}", e)
                callback.onError("Task failed: ${e.message}", e)
            }
        }
    }
    
    private fun findEditableElement(element: ElementNode): ElementNode? {
        // Check if this element is editable
        try {
            if (element.nodeInfo.isEditable) {
                return element
            }
        } catch (e: Exception) {
            // Node might be stale, continue searching
        }
        
        // Recursively search children
        for (child in element.children) {
            val found = findEditableElement(child)
            if (found != null) return found
        }
        
        return null
    }
    
    private fun setupLifecycleCallbacks() {
        val context = this.context ?: return
        if (context is Application) {
            lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
                override fun onActivityStarted(activity: android.app.Activity) {}
                override fun onActivityResumed(activity: android.app.Activity) {}
                override fun onActivityPaused(activity: android.app.Activity) {}
                override fun onActivityStopped(activity: android.app.Activity) {}
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
                override fun onActivityDestroyed(activity: android.app.Activity) {}
            }
            context.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }
    
    /**
     * Cleanup and release resources
     * Should be called when SDK is no longer needed
     */
    fun release() {
        val context = this.context
        if (context is Application && lifecycleCallbacks != null) {
            context.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
        stateCacheManager.clear()
        executorService.shutdown()
        INSTANCE = null
        Log.d(TAG, "DroidrunSDK released")
    }
    
    /**
     * Data class for combined state
     */
    data class CombinedState(
        val elements: List<ElementNode>,
        val phoneState: PhoneState
    )
    
    /**
     * Data class for full combined state
     */
    data class CombinedStateFull(
        val fullTree: JSONObject,
        val phoneState: PhoneState,
        val deviceContext: JSONObject
    )
}

