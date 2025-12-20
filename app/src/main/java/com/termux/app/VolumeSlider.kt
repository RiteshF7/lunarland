package com.termux.app

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
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
    
    // Fixed height in pixels (60 dp)
    private val fixedHeightPx: Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        60f,
        context.resources.displayMetrics
    ).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        
        // Set minimum height to enforce the fixed height
        minimumHeight = fixedHeightPx
        
        addView(progressView)

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val width = this.width
                    if (width > 0) {
                        val x = event.x.coerceIn(0f, width.toFloat())
                        val fraction = x / width
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
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Enforce fixed height
        val heightSpec = View.MeasureSpec.makeMeasureSpec(
            fixedHeightPx,
            View.MeasureSpec.EXACTLY
        )
        super.onMeasure(widthMeasureSpec, heightSpec)
    }

    private fun updateProgress() {
        val range = max - min
        if (range <= 0 || width == 0 || height == 0) return

        val fraction = ((currentVolume - min).toFloat() / range).coerceIn(0f, 1f)
        val progressWidth = (width * fraction).toInt()

        progressView.layoutParams = LayoutParams(
            progressWidth,
            LayoutParams.MATCH_PARENT
        )
    }
}

