package lunar.land.ui.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AISphere(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "AISphereRotation")
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 20000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val sizePx = with(density) { size.toPx() }
                drawAISphere(
                    size = sizePx,
                    rotationAngle = rotationAngle
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Empty content - sphere is drawn in drawBehind
    }
}

private fun DrawScope.drawAISphere(
    size: Float,
    rotationAngle: Float
) {
    val center = Offset(size / 2f, size / 2f)
    val radius = size * 0.4f
    
    // Draw star field inside the sphere
    drawStarField(
        center = center,
        radius = radius * 0.9f,
        count = 150
    )
    
    // Draw the sphere with iridescent colors
    rotate(rotationAngle, center) {
        drawIridescentSphere(
            center = center,
            radius = radius
        )
    }
}

private fun DrawScope.drawStarField(
    center: Offset,
    radius: Float,
    count: Int
) {
    val random = Random(42) // Fixed seed for consistent star pattern
    
    repeat(count) {
        val angle = random.nextFloat() * 360f
        val distance = random.nextFloat() * radius
        val x = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * distance
        val y = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * distance
        
        // Check if point is within circle
        val distFromCenter = kotlin.math.sqrt(
            (x - center.x) * (x - center.x) + (y - center.y) * (y - center.y)
        )
        
        if (distFromCenter <= radius) {
            val starSize = random.nextFloat() * 1.5f + 0.5f
            val alpha = random.nextFloat() * 0.8f + 0.2f
            
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = starSize,
                center = Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawIridescentSphere(
    center: Offset,
    radius: Float
) {
    // Create iridescent gradient colors
    val colors = listOf(
        Color(0xFF00FF88), // Green
        Color(0xFFFF6600), // Orange
        Color(0xFFFF0066), // Pink/Red
        Color(0xFF0066FF), // Blue
        Color(0xFF6600FF), // Purple
        Color(0xFF00FFFF), // Cyan
        Color(0xFFFFFF00), // Yellow
        Color(0xFF00FF88)  // Back to green
    )
    
    // Draw multiple overlapping circles with different gradients for iridescent effect
    val numLayers = 8
    
    repeat(numLayers) { layer ->
        val layerRadius = radius * (1f - layer * 0.08f)
        val angle = (layer * 45f) % 360f
        
        // Create gradient based on angle
        val startColor = colors[(layer % colors.size)]
        val endColor = colors[((layer + 1) % colors.size)]
        
        // Draw sphere outline with gradient
        val path = Path().apply {
            addOval(
                Rect(
                    left = center.x - layerRadius,
                    top = center.y - layerRadius,
                    right = center.x + layerRadius,
                    bottom = center.y + layerRadius
                )
            )
        }
        
        // Create radial gradient
        val gradient = Brush.radialGradient(
            colors = listOf(
                startColor.copy(alpha = 0.3f),
                endColor.copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = Offset(
                center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * layerRadius * 0.3f,
                center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * layerRadius * 0.3f
            ),
            radius = layerRadius
        )
        
        drawPath(
            path = path,
            brush = gradient
        )
    }
    
    // Draw main sphere outline with iridescent border
    val borderPath = Path().apply {
        addOval(
            Rect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius
            )
        )
    }
    
    // Create sweeping gradient for the border
    val borderGradient = Brush.sweepGradient(
        colors = colors,
        center = center
    )
    
    // Draw border with varying opacity
    drawPath(
        path = borderPath,
        brush = borderGradient,
        alpha = 0.6f
    )
    
    // Draw additional highlights for depth
    val highlightRadius = radius * 0.7f
    val highlightOffset = Offset(
        center.x - radius * 0.2f,
        center.y - radius * 0.2f
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.2f),
                Color.Transparent
            ),
            center = highlightOffset,
            radius = highlightRadius
        ),
        radius = highlightRadius,
        center = highlightOffset
    )
}

