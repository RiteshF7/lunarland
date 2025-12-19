package com.termux.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingVolumeView(context: Context) : LinearLayout(context) {
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    private val keyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private var currentVolume: Int = 0
    private var maxVolume: Int = 0
    
    // Swipe gesture detection
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isSwipeGesture = false
    private val swipeThreshold = 50f // Minimum distance for swipe
    private var lastClickTime = 0L
    private val clickDelay = 200L // Max time between down and up for click
    
    private var volumeChangeReceiver: android.content.BroadcastReceiver? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        
        // Initialize volume
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // Make view completely invisible but still handle touches
        // View will be sized by WindowManager.LayoutParams in FloatingVolumeService
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false) // We'll override onDraw to draw nothing
        
        // Listen to system volume changes
        volumeChangeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        val volume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0)
                        updateVolume(volume, updateSystem = false)
                    }
                }
            }
        }
        context.registerReceiver(
            volumeChangeReceiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        )
        
        // Set touch listener for swipe gestures and click
        setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }
    }
    
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                isSwipeGesture = false
                lastClickTime = System.currentTimeMillis()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - initialTouchX
                val dy = event.y - initialTouchY
                
                // Check if it's a horizontal swipe
                if (abs(dx) > swipeThreshold && abs(dx) > abs(dy)) {
                    isSwipeGesture = true
                    
                    // Determine swipe direction and adjust volume
                    // Swipe right (positive dx) = volume up
                    // Swipe left (negative dx) = volume down
                    val volumeStep = 1 // Change volume by 1 step per swipe threshold
                    val swipeSteps = (abs(dx) / swipeThreshold).toInt()
                    
                    val volumeChange = if (dx > 0) {
                        // Swipe right - volume up
                        swipeSteps
                    } else {
                        // Swipe left - volume down
                        -swipeSteps
                    }
                    
                    if (volumeChange != 0) {
                        val newVolume = (currentVolume + volumeChange).coerceIn(0, maxVolume)
                        updateVolume(newVolume, updateSystem = true)
                        
                        // Reset initial position to prevent accumulation
                        initialTouchX = event.x
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val clickDuration = System.currentTimeMillis() - lastClickTime
                val dx = event.x - initialTouchX
                val dy = event.y - initialTouchY
                
                // Check if it was a quick swipe (not just a click)
                if (abs(dx) > swipeThreshold && abs(dx) > abs(dy)) {
                    // Final swipe adjustment
                    isSwipeGesture = true
                    val volumeChange = if (dx > 0) 1 else -1
                    val newVolume = (currentVolume + volumeChange).coerceIn(0, maxVolume)
                    updateVolume(newVolume, updateSystem = true)
                } else if (!isSwipeGesture && clickDuration < clickDelay) {
                    // Single click - lock screen
                    lockScreen()
                }
                true
            }
            else -> false
        }
    }
    
    private fun updateVolume(newVolume: Int, updateSystem: Boolean) {
        val clampedVolume = newVolume.coerceIn(0, maxVolume)
        if (clampedVolume != currentVolume) {
            currentVolume = clampedVolume
            // Don't update text view since it's invisible
            // No need to invalidate since we're not drawing anything
            
            if (updateSystem) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    currentVolume,
                    AudioManager.FLAG_PLAY_SOUND
                )
            }
        }
    }
    
    private fun lockScreen() {
        try {
            // Use device admin if available (requires device admin permission)
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            devicePolicyManager?.lockNow()
        } catch (e: Exception) {
            // Device admin not available, try alternative method
            try {
                // Send screen off intent (requires system permission, may not work)
                val intent = Intent(Intent.ACTION_SCREEN_OFF)
                context.sendBroadcast(intent)
            } catch (ex: Exception) {
                // If all methods fail, silently ignore
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        // Don't draw anything - view is completely invisible
        // Touch events still work because the view exists and has size
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        volumeChangeReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
    }
}

