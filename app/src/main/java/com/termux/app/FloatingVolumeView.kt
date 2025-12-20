package com.termux.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    
    private lateinit var volumeSlider: VolumeSlider
    
    // Swipe gesture detection
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isSwipeGesture = false
    private val swipeThreshold = 50f // Minimum distance for swipe
    private var lastClickTime = 0L
    private val clickDelay = 200L // Max time between down and up for click
    
    private val handler = Handler(Looper.getMainLooper())
    private var hideSliderRunnable: Runnable? = null
    private val sliderHideDelay = 1000L // Hide slider after 1 second of no swipe
    
    var onUpdateTouchableState: ((touchable: Boolean) -> Unit)? = null
    
    private var volumeChangeReceiver: android.content.BroadcastReceiver? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        
        // Initialize volume
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // Create and add volume slider
        volumeSlider = VolumeSlider(context).apply {
            min = 0
            max = this@FloatingVolumeView.maxVolume
            currentVolume = this@FloatingVolumeView.currentVolume
            onVolumeChanged = { volume ->
                this@FloatingVolumeView.updateVolume(volume, updateSystem = true)
            }
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(volumeSlider)
        
        // Set transparent background
        setBackgroundColor(Color.TRANSPARENT)
        // Make entire view invisible by default (transparent)
        alpha = 0f
        // Make view not clickable/focusable by default to allow touches to pass through
        isClickable = false
        isFocusable = false
        setWillNotDraw(false) // We'll override onDraw if needed
        
        // Listen to system volume changes
        volumeChangeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        val volume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0)
                        updateVolume(volume, updateSystem = false)
                        // Show view when volume changes via system (hardware buttons, etc.)
                        showVolumeSlider()
                        scheduleHideVolumeSlider()
                    }
                }
            }
        }
        context.registerReceiver(
            volumeChangeReceiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        )
        
        // Don't set onTouchListener - we'll override onTouchEvent instead
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If view is not touchable (invisible), don't handle touches
        if (alpha < 0.1f && !isClickable) {
            return false
        }
        return handleTouchEvent(event)
    }
    
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                isSwipeGesture = false
                lastClickTime = System.currentTimeMillis()
                // Return true to receive MOVE events so we can detect swipes
                // But we'll return false quickly in MOVE if it's not a swipe
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - initialTouchX
                val dy = event.y - initialTouchY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
                
                // Only start handling if we detect clear horizontal movement
                if (distance > swipeThreshold * 0.3f) {
                    // Check if it's a horizontal swipe (horizontal movement is significantly more than vertical)
                    if (abs(dx) > abs(dy) * 1.5f) {
                        // This is a horizontal swipe - start intercepting
                        if (!isSwipeGesture) {
                            isSwipeGesture = true
                            // Show volume slider (make it fully visible)
                            showVolumeSlider()
                        }
                        
                        // Determine swipe direction and adjust volume
                        // Swipe right (positive dx) = volume up
                        // Swipe left (negative dx) = volume down
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
                        
                        // Intercept this event since we're handling the swipe
                        true
                    } else {
                        // Vertical or diagonal gesture - don't intercept, let it pass through
                        false
                    }
                } else {
                    // Very small movement - might be a tap, don't intercept
                    false
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - initialTouchX
                val dy = event.y - initialTouchY
                
                if (isSwipeGesture && abs(dx) > abs(dy) * 1.5f) {
                    // We were handling a swipe, finalize it
                    val volumeChange = if (dx > 0) 1 else -1
                    val newVolume = (currentVolume + volumeChange).coerceIn(0, maxVolume)
                    updateVolume(newVolume, updateSystem = true)
                    // Hide slider after delay
                    scheduleHideVolumeSlider()
                    // Intercept since we handled the swipe
                    true
                } else {
                    // Was a tap or vertical gesture - don't intercept, let it pass through
                    false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isSwipeGesture) {
                    scheduleHideVolumeSlider()
                }
                false
            }
            else -> false
        }
    }
    
    private fun updateVolume(newVolume: Int, updateSystem: Boolean) {
        val clampedVolume = newVolume.coerceIn(0, maxVolume)
        if (clampedVolume != currentVolume) {
            currentVolume = clampedVolume
            // Update volume slider
            volumeSlider.currentVolume = currentVolume
            
            if (updateSystem) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    currentVolume,
                    AudioManager.FLAG_PLAY_SOUND
                )
            }
        }
    }
    
    private fun showVolumeSlider() {
        // Cancel any pending hide operation
        hideSliderRunnable?.let { handler.removeCallbacks(it) }
        
        // Make view touchable
        onUpdateTouchableState?.invoke(true)
        
        // Animate entire view to full opacity (make it visible)
        animate()
            .alpha(1.0f)
            .setDuration(200)
            .start()
    }
    
    private fun scheduleHideVolumeSlider() {
        // Cancel any existing hide operation
        hideSliderRunnable?.let { handler.removeCallbacks(it) }
        
        // Schedule hide after delay
        hideSliderRunnable = Runnable {
            hideVolumeSlider()
        }
        handler.postDelayed(hideSliderRunnable!!, sliderHideDelay)
    }
    
    private fun hideVolumeSlider() {
        // Animate entire view back to invisible (completely transparent)
        animate()
            .alpha(0f)
            .setDuration(300)
            .start()
        
        // Make view not touchable after animation completes
        handler.postDelayed({
            onUpdateTouchableState?.invoke(false)
        }, 300)
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
        super.onDraw(canvas)
        // Transparent background - no drawing needed
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any pending hide operations
        hideSliderRunnable?.let { handler.removeCallbacks(it) }
        
        volumeChangeReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
    }
}

