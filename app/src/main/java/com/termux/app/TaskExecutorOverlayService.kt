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
import androidx.compose.ui.unit.dp
import com.termux.shared.logger.Logger

/**
 * Service that displays a floating overlay window showing task logs and stop button
 */
class TaskExecutorOverlayService : Service() {
    
    private val LOG_TAG = "TaskExecutorOverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    companion object {
        private var logText: String = ""
        private var onStopClick: (() -> Unit)? = null
        private var isVisible: Boolean = false
        
        fun updateLogs(text: String) {
            logText = text
        }
        
        fun setStopCallback(callback: (() -> Unit)?) {
            onStopClick = callback
        }
        
        fun isOverlayVisible(): Boolean = isVisible
    }
    
    override fun onCreate() {
        super.onCreate()
        Logger.logInfo(LOG_TAG, "Overlay service created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logInfo(LOG_TAG, "Overlay service started")
        showOverlay()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun showOverlay() {
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 100
            width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            height = (resources.displayMetrics.heightPixels * 0.7).toInt()
        }
        
        // Create Compose view for overlay
        val composeView = ComposeView(this).apply {
            setContent {
                OverlayContent(
                    logText = logText,
                    onStopClick = {
                        onStopClick?.invoke()
                    },
                    onCloseClick = {
                        hideOverlay()
                    }
                )
            }
        }
        
        overlayView = composeView
        isVisible = true
        
        try {
            windowManager?.addView(overlayView, params)
            Logger.logInfo(LOG_TAG, "Overlay window added")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to add overlay window: ${e.message}")
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
        hideOverlay()
        Logger.logInfo(LOG_TAG, "Overlay service destroyed")
    }
}

@Composable
fun OverlayContent(
    logText: String,
    onStopClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    
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
            
            // Stop button
            Button(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Task", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

