package com.termux.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import kotlin.math.roundToInt

class VolumeSlider(context: Context) : LinearLayout(context) {
    var min: Int = 0
    var max: Int = 100
    var currentVolume: Int = 50
        set(value) {
            field = value.coerceIn(min, max)
            updateProgress()
        }
    
    var onVolumeChanged: ((Int) -> Unit)? = null

    private val progressView = View(context).apply {
        setBackgroundColor(Color.parseColor("#4CAF50"))
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.BOTTOM
        addView(progressView)

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val height = this.height
                    if (height > 0) {
                        val y = event.y.coerceIn(0f, height.toFloat())
                        val fraction = 1f - (y / height)
                        val newVolume = (min + fraction * (max - min)).roundToInt()
                        currentVolume = newVolume
                        onVolumeChanged?.invoke(currentVolume)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    true
                }
                else -> false
            }
        }

        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateProgress()
        }
    }

    private fun updateProgress() {
        val range = max - min
        if (range <= 0 || width == 0 || height == 0) return

        val fraction = ((currentVolume - min).toFloat() / range).coerceIn(0f, 1f)
        val progressHeight = (height * fraction).toInt()

        progressView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            progressHeight
        )
    }
}

