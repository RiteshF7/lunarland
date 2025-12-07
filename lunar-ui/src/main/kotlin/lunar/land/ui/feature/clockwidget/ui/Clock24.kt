package lunar.land.ui.feature.clockwidget.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.util.Log
import lunar.land.ui.core.model.Constants.Defaults.DEFAULT_CLOCK_24_ANALOG_RADIUS
import lunar.land.ui.core.ui.HorizontalSpacer
import lunar.land.ui.core.ui.VerticalSpacer
import lunar.land.ui.feature.clockwidget.model.AnalogClockHandlePhase
import lunar.land.ui.feature.clockwidget.model.AnalogClockPhase
import lunar.land.ui.feature.clockwidget.model.Digit
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun Clock24(
    modifier: Modifier = Modifier,
    currentTime: String,
    analogClockRadius: Float = DEFAULT_CLOCK_24_ANALOG_RADIUS,
    analogClockSpacing: Dp = 4.dp,
    digitSpacing: Dp = 4.dp,
    handleColor: Color = MaterialTheme.colorScheme.onSurface,
    handleWidth: Float = 4f,
    offsetAnimationSpec: AnimationSpec<Offset> = tween(durationMillis = 900),
    colorAnimationSpec: AnimationSpec<Color> = tween(durationMillis = 900)
) {
    val timeList = remember(key1 = currentTime) {
        Log.d("Clock24", "Raw currentTime: '$currentTime' (length: ${currentTime.length})")
        // Log each character and its code
        currentTime.toCharArray().forEachIndexed { index, char ->
            Log.d("Clock24", "  Char[$index]: '$char' (code: ${char.code.toChar()})")
        }
        
        // Trim and only keep numeric digits (0-9), filter out everything else
        // For HH:mm format, we should have exactly 4 digits
        val cleanedTime = currentTime.trim()
        val allDigits = cleanedTime.toCharArray().filter { it.isDigit() }
        Log.d("Clock24", "All digits found: ${allDigits.joinToString("")}")
        
        val digits = allDigits
            .take(4) // Ensure we only take 4 digits for HH:mm format
            .map { it.toString().toInt() }
        
        // Only proceed if we have exactly 4 digits
        if (digits.size != 4) {
            Log.w("Clock24", "Expected 4 digits but got ${digits.size}. Digits: $digits")
        }
        
        Log.d("Clock24", "Final extracted digits: $digits (count: ${digits.size})")
        digits.take(4) // Final safety check - only return 4 digits
    }

    // Only render if we have exactly 4 digits
    if (timeList.size != 4) {
        Log.w("Clock24", "Skipping render - expected 4 digits but got ${timeList.size}")
        return
    }
    
    Row(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        timeList.take(4).forEachIndexed { index, digit ->
            // Safety check: ensure digit is valid (0-9)
            if (digit !in 0..9) {
                Log.w("Clock24", "Invalid digit: $digit at index $index, skipping")
                return@forEachIndexed
            }
            
            DigitWithAnalogClocks(
                digit = Digit.ALL[digit],
                analogClockRadius = analogClockRadius,
                analogClockSpacing = analogClockSpacing,
                handleColor = handleColor,
                handleWidth = handleWidth,
                offsetAnimationSpec = offsetAnimationSpec,
                colorAnimationSpec = colorAnimationSpec
            )
            if (index != timeList.lastIndex) {
                HorizontalSpacer(spacing = digitSpacing)
            }
        }
    }
}

@Composable
private fun DigitWithAnalogClocks(
    digit: Digit,
    analogClockRadius: Float,
    analogClockSpacing: Dp,
    handleColor: Color,
    handleWidth: Float,
    offsetAnimationSpec: AnimationSpec<Offset> = tween(durationMillis = 900),
    colorAnimationSpec: AnimationSpec<Color> = tween(durationMillis = 900)
) {
    Column {
        digit.analogHandles.forEachIndexed { index, list ->
            Row {
                AnalogClock(
                    radius = analogClockRadius,
                    analogClockPhase = list.first(),
                    handleColor = handleColor,
                    handleWidth = handleWidth,
                    offsetAnimationSpec = offsetAnimationSpec,
                    colorAnimationSpec = colorAnimationSpec
                )
                HorizontalSpacer(spacing = analogClockSpacing)
                AnalogClock(
                    radius = analogClockRadius,
                    analogClockPhase = list.last(),
                    handleColor = handleColor,
                    handleWidth = handleWidth,
                    offsetAnimationSpec = offsetAnimationSpec,
                    colorAnimationSpec = colorAnimationSpec
                )
            }
            if (index != digit.analogHandles.lastIndex) {
                VerticalSpacer(spacing = analogClockSpacing)
            }
        }
    }
}

@Composable
private fun AnalogClock(
    modifier: Modifier = Modifier,
    radius: Float,
    analogClockPhase: AnalogClockPhase,
    handleColor: Color,
    handleWidth: Float,
    offsetAnimationSpec: AnimationSpec<Offset> = tween(durationMillis = 900),
    colorAnimationSpec: AnimationSpec<Color> = tween(durationMillis = 900)
) {
    val size = LocalDensity.current.run { radius.toDp() * 2 }
    val center = Offset(x = radius, y = radius)
    val disabledColor = handleColor.copy(alpha = 0f) // Fully transparent for NONE handles

    fun offsetFromAngle(angle: Double) = Offset(
        x = radius * cos(x = Math.toRadians(angle)).toFloat(),
        y = radius * sin(x = Math.toRadians(angle)).toFloat()
    ) + center

    val endFirst by animateOffsetAsState(
        label = "End first offset",
        targetValue = offsetFromAngle(angle = analogClockPhase.first.angle),
        animationSpec = offsetAnimationSpec
    )
    val endSecond by animateOffsetAsState(
        label = "End second offset",
        targetValue = offsetFromAngle(angle = analogClockPhase.second.angle),
        animationSpec = offsetAnimationSpec
    )
    val handleColorFirst by animateColorAsState(
        label = "Handle color first",
        targetValue = if (analogClockPhase.first == AnalogClockHandlePhase.NONE) disabledColor else handleColor,
        animationSpec = colorAnimationSpec
    )
    val handleColorSecond by animateColorAsState(
        label = "handle color second",
        targetValue = if (analogClockPhase.second == AnalogClockHandlePhase.NONE) disabledColor else handleColor,
        animationSpec = colorAnimationSpec
    )

    Canvas(
        modifier = modifier.size(size = size)
    ) {
        // Only draw lines if the handle is not NONE (fully transparent)
        if (analogClockPhase.first != AnalogClockHandlePhase.NONE && handleColorFirst.alpha > 0f) {
            drawLine(
                color = handleColorFirst,
                start = center,
                end = endFirst,
                strokeWidth = handleWidth
            )
        }
        if (analogClockPhase.second != AnalogClockHandlePhase.NONE && handleColorSecond.alpha > 0f) {
            drawLine(
                color = handleColorSecond,
                start = center,
                end = endSecond,
                strokeWidth = handleWidth
            )
        }
    }
}

