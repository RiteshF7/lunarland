package com.termux.app

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.termux.droidrun.portal.DroidrunSDK
import com.termux.droidrun.wrapper.bridge.PythonBridge
import com.termux.droidrun.wrapper.bridge.StateBridge
import com.termux.droidrun.wrapper.tools.CommandExecutor
import com.termux.droidrun.wrapper.tools.TermuxCommandExecutor
import com.termux.droidrun.wrapper.utils.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DroidrunTestActivity - Test activity for verifying droidrun integration
 * 
 * This activity tests:
 * 1. SDK initialization
 * 2. Python environment setup
 * 3. Wheel installation
 * 4. Basic droidrun functionality
 */
class DroidrunTestActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var testButton: Button
    private lateinit var initButton: Button
    private var overlayToggleButton: Button? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false
    private var isOverlayVisible = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create simple layout programmatically
        scrollView = ScrollView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        val linearLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        statusText = TextView(this).apply {
            text = "Ready to test droidrun integration"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        
        // Control buttons
        val enableAccessibilityButton = Button(this).apply {
            text = "Enable Accessibility Service"
            setOnClickListener { enableAccessibilityService() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }
        
        val enableKeyboardButton = Button(this).apply {
            text = "Enable Keyboard IME"
            setOnClickListener { enableKeyboardIME() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }
        
        overlayToggleButton = Button(this).apply {
            text = "Show Overlay"
            setOnClickListener { toggleOverlay() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        
        logText = TextView(this).apply {
            text = "Logs will appear here...\n"
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 16, 0, 16)
        }
        
        initButton = Button(this).apply {
            text = "1. Initialize SDK & Python"
            setOnClickListener { initializeSDK() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        
        testButton = Button(this).apply {
            text = "2. Test Wheel Installation"
            isEnabled = false
            setOnClickListener { testWheelInstallation() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        
        val testBasicButton = Button(this).apply {
            text = "3. Test Basic Functionality"
            isEnabled = false
            setOnClickListener { testBasicFunctionality() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        linearLayout.addView(statusText)
        linearLayout.addView(enableAccessibilityButton)
        linearLayout.addView(enableKeyboardButton)
        linearLayout.addView(overlayToggleButton)
        linearLayout.addView(initButton)
        linearLayout.addView(testButton)
        linearLayout.addView(testBasicButton)
        linearLayout.addView(logText)
        
        scrollView.addView(linearLayout)
        setContentView(scrollView)
        
        // Initialize SDK
        DroidrunSDK.initialize(applicationContext)
        appendLog("SDK initialized")
    }
    
    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            logText.append("[$timestamp] $message\n")
            // Scroll to bottom
            scrollView.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }
    
    private fun initializeSDK() {
        if (isRunning) return
        isRunning = true
        initButton.isEnabled = false
        statusText.text = "Initializing..."
        
        coroutineScope.launch {
            try {
                appendLog("Starting SDK and Python initialization...")
                
                // Check permissions
                val permissionManager = PermissionManager(this@DroidrunTestActivity)
                appendLog("Checking permissions...")
                
                val hasAccessibility = permissionManager.isAccessibilityServiceEnabled()
                val hasKeyboard = permissionManager.isKeyboardIMEEnabled()
                
                appendLog("Accessibility Service: ${if (hasAccessibility) "Enabled" else "Disabled"}")
                appendLog("Keyboard IME: ${if (hasKeyboard) "Enabled" else "Disabled"}")
                
                if (!hasAccessibility || !hasKeyboard) {
                    appendLog("WARNING: Some permissions are missing. Features may not work correctly.")
                    appendLog("Please enable Droidrun services in Android settings.")
                }
                
                // Initialize Python environment
                appendLog("Initializing Python environment...")
                val commandExecutor = TermuxCommandExecutor(this@DroidrunTestActivity)
                val stateBridge = StateBridge(this@DroidrunTestActivity)
                val pythonBridge = PythonBridge(this@DroidrunTestActivity, commandExecutor, stateBridge)
                
                val pythonInitialized = pythonBridge.initializePythonEnvironment()
                
                if (pythonInitialized) {
                    appendLog("✓ Python environment initialized successfully")
                    statusText.text = "Initialization complete!"
                    testButton.isEnabled = true
                } else {
                    appendLog("✗ Python environment initialization failed")
                    statusText.text = "Initialization failed - check logs"
                }
                
            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                appendLog(e.stackTraceToString())
                statusText.text = "Error: ${e.message}"
            } finally {
                isRunning = false
                initButton.isEnabled = true
            }
        }
    }
    
    private fun testWheelInstallation() {
        if (isRunning) return
        isRunning = true
        testButton.isEnabled = false
        statusText.text = "Testing wheel installation..."
        
        coroutineScope.launch {
            try {
                appendLog("Testing wheel installation...")
                
                val commandExecutor = TermuxCommandExecutor(this@DroidrunTestActivity)
                
                // Check if Python is available
                appendLog("Checking Python version...")
                val pythonCheck = withContext(Dispatchers.IO) {
                    commandExecutor.execute("python3", listOf("--version"))
                }
                
                if (pythonCheck.success) {
                    appendLog("✓ Python found: ${pythonCheck.stdout.trim()}")
                } else {
                    appendLog("✗ Python not found: ${pythonCheck.stderr}")
                    statusText.text = "Python not available"
                    return@launch
                }
                
                // Check if droidrun is installed
                appendLog("Checking if droidrun package is installed...")
                val pipCheck = withContext(Dispatchers.IO) {
                    commandExecutor.execute("pip3", listOf("show", "droidrun"))
                }
                
                if (pipCheck.success) {
                    appendLog("✓ droidrun package is installed")
                    appendLog("Package info:")
                    pipCheck.stdout.lines().take(10).forEach { line ->
                        if (line.isNotBlank()) {
                            appendLog("  $line")
                        }
                    }
                    statusText.text = "Wheel installation verified!"
                } else {
                    appendLog("✗ droidrun package not found")
                    appendLog("Attempting to install from local wheel...")
                    
                    // Try to install from assets
                    val stateBridge = StateBridge(this@DroidrunTestActivity)
                    val pythonBridge = PythonBridge(this@DroidrunTestActivity, commandExecutor, stateBridge)
                    val installed = pythonBridge.initializePythonEnvironment()
                    
                    if (installed) {
                        appendLog("✓ droidrun installed from local wheel")
                        statusText.text = "Wheel installation successful!"
                    } else {
                        appendLog("✗ Failed to install droidrun from wheel")
                        statusText.text = "Wheel installation failed"
                    }
                }
                
                // Test importing droidrun
                appendLog("Testing droidrun import...")
                val importTest = withContext(Dispatchers.IO) {
                    commandExecutor.execute("python3", listOf("-c", "import droidrun; print('droidrun version:', droidrun.__version__ if hasattr(droidrun, '__version__') else 'unknown')"))
                }
                
                if (importTest.success) {
                    appendLog("✓ droidrun import successful")
                    appendLog("  ${importTest.stdout.trim()}")
                } else {
                    appendLog("✗ droidrun import failed: ${importTest.stderr}")
                }
                
            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                appendLog(e.stackTraceToString())
                statusText.text = "Error: ${e.message}"
            } finally {
                isRunning = false
                testButton.isEnabled = true
            }
        }
    }
    
    private fun testBasicFunctionality() {
        if (isRunning) return
        isRunning = true
        statusText.text = "Testing basic functionality..."
        
        coroutineScope.launch {
            try {
                appendLog("Testing basic droidrun functionality...")
                
                // Test SDK methods
                appendLog("Testing SDK methods...")
                val sdk = DroidrunSDK.getInstance()
                
                val isAccessibilityEnabled = sdk.isAccessibilityServiceEnabled()
                val isKeyboardEnabled = sdk.isKeyboardIMEEnabled()
                
                appendLog("Accessibility Service: ${if (isAccessibilityEnabled) "Enabled" else "Disabled"}")
                appendLog("Keyboard IME: ${if (isKeyboardEnabled) "Enabled" else "Disabled"}")
                
                // Test getting formatted state (if accessibility is enabled)
                if (isAccessibilityEnabled) {
                    appendLog("Testing getFormattedState()...")
                    try {
                        val state = withContext(Dispatchers.IO) {
                            sdk.getFormattedState()
                        }
                        appendLog("✓ getFormattedState() successful")
                        appendLog("  Elements: ${state.formattedElements.size}")
                        appendLog("  Package: ${state.phoneState.packageName ?: "unknown"}")
                        appendLog("  App: ${state.phoneState.appName ?: "unknown"}")
                    } catch (e: Exception) {
                        appendLog("✗ getFormattedState() failed: ${e.message}")
                    }
                } else {
                    appendLog("Skipping getFormattedState() - accessibility not enabled")
                }
                
                // Test StateBridge
                appendLog("Testing StateBridge...")
                val stateBridge = StateBridge(this@DroidrunTestActivity)
                val statePath = stateBridge.getDeviceStatePath()
                val actionsPath = stateBridge.getPythonActionsPath()
                val resultPath = stateBridge.getActionResultPath()
                
                appendLog("✓ StateBridge paths:")
                appendLog("  Device State: $statePath")
                appendLog("  Actions: $actionsPath")
                appendLog("  Results: $resultPath")
                
                statusText.text = "Basic functionality test complete!"
                
            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
                appendLog(e.stackTraceToString())
                statusText.text = "Error: ${e.message}"
            } finally {
                isRunning = false
            }
        }
    }
    
    private fun enableAccessibilityService() {
        appendLog("Opening Accessibility Settings...")
        val permissionManager = PermissionManager(this)
        val intent = permissionManager.getAccessibilitySettingsIntent()
        try {
            startActivity(intent)
            appendLog("✓ Opened Accessibility Settings")
            appendLog("Please enable 'Droidrun Accessibility Service' in the settings")
        } catch (e: Exception) {
            appendLog("✗ Failed to open Accessibility Settings: ${e.message}")
        }
    }
    
    private fun enableKeyboardIME() {
        appendLog("Opening Input Method Settings...")
        val permissionManager = PermissionManager(this)
        val intent = permissionManager.getInputMethodSettingsIntent()
        try {
            startActivity(intent)
            appendLog("✓ Opened Input Method Settings")
            appendLog("Please enable 'Droidrun Keyboard' in the settings")
        } catch (e: Exception) {
            appendLog("✗ Failed to open Input Method Settings: ${e.message}")
        }
    }
    
    private fun toggleOverlay() {
        try {
            val sdk = DroidrunSDK.getInstance()
            isOverlayVisible = !isOverlayVisible
            sdk.setOverlayVisible(isOverlayVisible)
            
            overlayToggleButton?.text = if (isOverlayVisible) "Hide Overlay" else "Show Overlay"
            appendLog(if (isOverlayVisible) "✓ Overlay shown" else "✓ Overlay hidden")
            
            // Update button text based on actual state
            val actualState = sdk.isOverlayVisible()
            if (actualState != isOverlayVisible) {
                isOverlayVisible = actualState
                overlayToggleButton?.text = if (isOverlayVisible) "Hide Overlay" else "Show Overlay"
            }
        } catch (e: Exception) {
            appendLog("✗ Failed to toggle overlay: ${e.message}")
            appendLog("Note: Overlay requires Accessibility Service to be enabled")
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update overlay button state when returning to activity
        try {
            val sdk = DroidrunSDK.getInstance()
            isOverlayVisible = sdk.isOverlayVisible()
            overlayToggleButton?.text = if (isOverlayVisible) "Hide Overlay" else "Show Overlay"
        } catch (e: Exception) {
            // SDK not initialized yet
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}

