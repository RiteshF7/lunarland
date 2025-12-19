package com.termux.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FloatingVolumeView(context: Context) : LinearLayout(context) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val slider = VolumeSlider(context).apply {
        min = 0
        max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        onVolumeChanged = { volume ->
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                volume,
                AudioManager.FLAG_PLAY_SOUND
            )
        }
        
        layoutParams = LayoutParams(50, 300)
    }

    val handleView = ImageView(context).apply {
        setBackgroundColor(Color.argb(128, 200, 200, 200))
        alpha = 0.5f
        layoutParams = LayoutParams(50, 50).apply {
            setMargins(0, 10, 0, 0)
        }
    }

    private var volumeChangeReceiver: android.content.BroadcastReceiver? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.argb(200, 0, 0, 0))
        setPadding(10, 10, 10, 10)
        
        addView(slider)
        addView(handleView)
        
        // Listen to system volume changes
        volumeChangeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        val volume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0)
                        slider.currentVolume = volume
                    }
                }
            }
        }
        context.registerReceiver(
            volumeChangeReceiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        )
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
        job.cancel()
    }
}

