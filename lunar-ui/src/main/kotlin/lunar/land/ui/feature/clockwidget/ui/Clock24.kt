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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    // Parse time string (format: "hh:mm a" or "h:mm a")
    val timeData = remember(key1 = currentTime) {
        Log.d("Clock24", "Raw currentTime: '$currentTime'")
        val cleanedTime = currentTime.trim()
        
        // Extract AM/PM
        val amPm = if (cleanedTime.contains("AM", ignoreCase = true)) "AM"
                   else if (cleanedTime.contains("PM", ignoreCase = true)) "PM"
                   else ""
        
        // Extract digits only
        val allDigits = cleanedTime.toCharArray().filter { it.isDigit() }
        Log.d("Clock24", "All digits found: ${allDigits.joinToString("")}, AM/PM: $amPm")
        
        // For 12-hour format, we should have 3 or 4 digits (h:mm or hh:mm)
        val digits = allDigits.take(4).map { it.toString().toInt() }
        
        // Pad to 4 digits if needed (e.g., "7:35" -> "0735")
        val paddedDigits = if (digits.size == 3) {
            listOf(0) + digits // Add leading zero for single digit hour
        } else {
            digits.take(4)
        }
        
        Log.d("Clock24", "Final digits: $paddedDigits, AM/PM: $amPm")
        Pair(paddedDigits, amPm)
    }
    
    val (timeList, amPm) = timeData
    
    // Only render if we have exactly 4 digits
    if (timeList.size != 4) {
        Log.w("Clock24", "Skipping render - expected 4 digits but got ${timeList.size}")
        return
    }
    
    Row(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hour digits (first 2)
        timeList.take(2).forEachIndexed { index, digit ->
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
            if (index < 1) {
                HorizontalSpacer(spacing = digitSpacing)
            }
        }
        
        // Colon separator - size it to match digit height
        val colonTextSize = with(LocalDensity.current) {
            // Digit height is approximately 3 analog clocks + 2 spacings
            val clockSizePx = analogClockRadius * 2
            val spacingPx = analogClockSpacing.toPx()
            val digitHeightPx = clockSizePx * 3 + spacingPx * 2
            digitHeightPx.toSp()
        }
        Text(
            text = ":",
            color = handleColor,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = colonTextSize,
                fontWeight = FontWeight.Normal
            ),
            modifier = Modifier.padding(horizontal = digitSpacing)
        )
        
        // Minute digits (last 2)
        timeList.drop(2).forEachIndexed { index, digit ->
            if (digit !in 0..9) {
                Log.w("Clock24", "Invalid digit: $digit at index ${index + 2}, skipping")
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
            if (index < 1) {
                HorizontalSpacer(spacing = digitSpacing)
            }
        }
        
        // AM/PM indicator - size it to match digit height
        if (amPm.isNotEmpty()) {
            HorizontalSpacer(spacing = digitSpacing)
            val amPmTextSize = with(LocalDensity.current) {
                // Digit height is approximately 3 analog clocks + 2 spacings
                val clockSizePx = analogClockRadius * 2
                val spacingPx = analogClockSpacing.toPx()
                val digitHeightPx = clockSizePx * 3 + spacingPx * 2
                digitHeightPx.toSp()
            }
            Text(
                text = amPm,
                color = handleColor,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = amPmTextSize,
                    fontWeight = FontWeight.Normal
                ),
                modifier = Modifier.padding(start = digitSpacing)
            )
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

