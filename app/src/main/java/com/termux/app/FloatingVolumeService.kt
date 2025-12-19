package com.termux.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.termux.R

class FloatingVolumeService : Service() {
    private val windowManager: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private val floatingVolumeView by lazy {
        FloatingVolumeView(this)
    }

    private val layoutParams: WindowManager.LayoutParams by lazy {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        
        // Set explicit dimensions for invisible touch area
        // Wide enough for horizontal swipes, tall enough for touch detection
        val touchAreaWidth = (screenWidth * 0.8).toInt() // 80% of screen width
        val touchAreaHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 60f, displayMetrics
        ).toInt()
        
        WindowManager.LayoutParams(
            touchAreaWidth,
            touchAreaHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // Position at bottom center with some padding from bottom (5% of screen height)
            y = (screenHeight * 0.05).toInt()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "floating_volume_channel"
        const val ACTION_START = "com.termux.app.START_FLOATING_VOLUME"
        const val ACTION_STOP = "com.termux.app.STOP_FLOATING_VOLUME"
        private const val TAG = "FloatingVolumeService"
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        configureTouchListener()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Volume",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating volume control service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun configureTouchListener() {
        // Touch handling is now done in FloatingVolumeView itself
        // No need for separate drag handler since view is fixed at bottom
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        try {
            windowManager.addView(floatingVolumeView, layoutParams)
            Log.d(TAG, "Floating volume view added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating volume view", e)
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FloatingVolumeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Volume")
            .setContentText("Volume control is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (floatingVolumeView.isAttachedToWindow) {
                windowManager.removeView(floatingVolumeView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating volume view", e)
        }
    }
}

