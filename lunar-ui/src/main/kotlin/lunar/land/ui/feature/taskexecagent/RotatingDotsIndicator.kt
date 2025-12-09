package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import lunar.land.ui.core.theme.LunarTheme

/**
 * Rotating dots indicator for voice listening state.
 * Shows dots rotating around a circle.
 */
@Composable
fun RotatingDotsIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotating_dots")
    
    // Rotation animation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Canvas(
        modifier = modifier.size(80.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.6f // Dots orbit at 60% of radius
        val dotCount = 8 // Number of dots
        val dotRadius = 4.dp.toPx()
        
        repeat(dotCount) { i ->
            // Calculate angle for each dot with rotation
            val baseAngle = (i * 360f / dotCount) * (kotlin.math.PI / 180f)
            val rotationAngleRad = (rotationAngle * kotlin.math.PI / 180f)
            val angle = baseAngle + rotationAngleRad
            
            // Calculate dot position
            val dotX = center.x + cos(angle).toFloat() * radius
            val dotY = center.y + sin(angle).toFloat() * radius
            
            // Vary opacity based on position (fade effect)
            val distanceFromTop = (dotY - (center.y - radius)) / (radius * 2f)
            val opacity = 0.4f + (distanceFromTop * 0.6f).coerceIn(0f, 1f)
            
            // Draw dot
            drawCircle(
                color = LunarTheme.AccentColor.copy(alpha = opacity),
                radius = dotRadius,
                center = Offset(dotX, dotY)
            )
        }
    }
}

