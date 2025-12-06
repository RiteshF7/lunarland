package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The glowing sphere visualizer with texture and pulse animation.
 * This is the central interactive element of the UI.
 */
@Composable
fun SphereVisualizer(
    modifier: Modifier = Modifier,
    isListening: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sphere_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glowing sphere background with multiple concentric circle layers
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * scale

            // Draw many concentric circles to create layered effect
            // Outer glow layers (fading out)
            repeat(8) { layer ->
                val glowRadius = baseRadius * (1f + (layer + 1) * 0.08f)
                val glowAlpha = (0.3f - layer * 0.03f).coerceAtLeast(0.05f)
                
                drawCircle(
                    color = Color(0xFF4DFF88).copy(alpha = glowAlpha),
                    radius = glowRadius,
                    center = center,
                    style = Stroke(
                        width = 2.dp.toPx()
                    )
                )
            }

            // Main concentric circle layers (creating the layered sphere effect)
            val numLayers = 20
            repeat(numLayers) { layer ->
                val layerRadius = baseRadius * (0.3f + (layer.toFloat() / numLayers) * 0.7f)
                val layerAlpha = when {
                    layer < numLayers / 4 -> 0.15f - (layer * 0.01f)
                    layer < numLayers / 2 -> 0.12f - ((layer - numLayers / 4) * 0.008f)
                    else -> 0.08f - ((layer - numLayers / 2) * 0.005f)
                }.coerceAtLeast(0.02f)
                
                val strokeWidthPx = when {
                    layer % 3 == 0 -> 1.5f.dp.toPx()
                    layer % 3 == 1 -> 1f.dp.toPx()
                    else -> 0.8f.dp.toPx()
                }
                
                drawCircle(
                    color = Color(0xFF4DFF88).copy(alpha = layerAlpha),
                    radius = layerRadius,
                    center = center,
                    style = Stroke(
                        width = strokeWidthPx
                    )
                )
            }

            // Additional inner circles for depth
            repeat(5) { layer ->
                val innerRadius = baseRadius * (0.1f + layer * 0.05f)
                drawCircle(
                    color = Color(0xFF4DFF88).copy(alpha = 0.2f - layer * 0.03f),
                    radius = innerRadius,
                    center = center,
                    style = Stroke(
                        width = 1.5f.dp.toPx()
                    )
                )
            }

            // Main sphere radial gradient overlay
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4DFF88).copy(alpha = 0.25f),
                        Color(0xFF4DFF88).copy(alpha = 0.15f),
                        Color(0xFF4DFF88).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )
        }

        // Inner content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isListening) {
                LoadingIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                StatusText(text = "Listening...")
            }
        }
    }
}

