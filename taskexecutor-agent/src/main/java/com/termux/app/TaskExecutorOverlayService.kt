package com.termux.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.unit.dp
import com.termux.shared.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service that displays a floating overlay window showing task logs and stop button
 */
class TaskExecutorOverlayService : Service(), LifecycleOwner {
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    
    private val LOG_TAG = "TaskExecutorOverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    companion object {
        private val _logTextState = MutableStateFlow("")
        val logTextState: StateFlow<String> = _logTextState.asStateFlow()
        private var onStopClick: (() -> Unit)? = null
        private var isVisible: Boolean = false
        
        fun updateLogs(text: String) {
            _logTextState.value = text
        }
        
        fun setStopCallback(callback: (() -> Unit)?) {
            onStopClick = callback
        }
        
        fun isOverlayVisible(): Boolean = isVisible
        
        fun getLogText(): String = _logTextState.value
    }
    
    override fun onCreate() {
        super.onCreate()
        Logger.logInfo(LOG_TAG, "Overlay service created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        // Note: Services don't need saved state restoration - we use a minimal implementation
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logInfo(LOG_TAG, "Overlay service started")
        // Start as foreground service to ensure it stays alive
        startForeground(1, createNotification())
        showOverlay()
        return START_STICKY
    }
    
    private fun createNotification(): android.app.Notification {
        val channelId = "task_executor_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Task Executor Overlay",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        return android.app.Notification.Builder(this, channelId)
            .setContentTitle("Task Executor")
            .setContentText("Showing task logs overlay")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun showOverlay() {
        Logger.logInfo(LOG_TAG, "showOverlay called")
        try {
            if (overlayView != null) {
                Logger.logWarn(LOG_TAG, "Overlay already shown")
                return
            }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 100
            width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            height = (resources.displayMetrics.heightPixels * 0.7).toInt()
        }
        
        Logger.logInfo(LOG_TAG, "Setting lifecycle state to RESUMED")
        // Set lifecycle state
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        
        Logger.logInfo(LOG_TAG, "Creating ComposeView")
        // Create Compose view for overlay
        // Use default recomposer with lifecycle and saved state registry
        val composeView = ComposeView(this).apply {
            Logger.logInfo(LOG_TAG, "Setting view composition strategy")
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            
            Logger.logInfo(LOG_TAG, "Setting lifecycle owner")
            setViewTreeLifecycleOwner(this@TaskExecutorOverlayService)
            
            // Note: SavedStateRegistry is not needed for Services - only LifecycleOwner is required
            
            Logger.logInfo(LOG_TAG, "Setting content")
            setContent {
                OverlayContent(
                    onStopClick = {
                        onStopClick?.invoke()
                    },
                    onCloseClick = {
                        hideOverlay()
                    }
                )
            }
            Logger.logInfo(LOG_TAG, "ComposeView created and content set")
        }
        
        overlayView = composeView
        isVisible = true
        
        Logger.logInfo(LOG_TAG, "Adding overlay view to window manager")
        try {
            windowManager?.addView(overlayView, params)
            Logger.logInfo(LOG_TAG, "Overlay window added successfully")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to add overlay window: ${e.message}")
            Logger.logStackTraceWithMessage(LOG_TAG, "Exception details", e)
            overlayView = null
            isVisible = false
        }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error in showOverlay: ${e.message}")
            Logger.logStackTraceWithMessage(LOG_TAG, "Exception details", e)
            overlayView = null
            isVisible = false
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Logger.logInfo(LOG_TAG, "Overlay window removed")
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to remove overlay window: ${e.message}")
            }
        }
        overlayView = null
        isVisible = false
        stopSelf()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        hideOverlay()
        Logger.logInfo(LOG_TAG, "Overlay service destroyed")
    }
}

@Composable
fun OverlayContent(
    onStopClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Observe log text state changes using StateFlow
    val logText by TaskExecutorOverlayService.logTextState.collectAsState()
    
    // Auto-scroll to bottom when log text changes
    LaunchedEffect(logText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task Executor",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                IconButton(onClick = onCloseClick) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
            
            // Logs area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                Text(
                    text = logText.ifEmpty { "Waiting for task output..." },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                )
            }
            
            // Stop button - always visible when overlay is shown
            // Make it more prominent and ensure it's clickable
            Button(
                onClick = {
                    Logger.logInfo("OverlayContent", "Stop button clicked")
                    onStopClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    "Stop Task", 
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}

