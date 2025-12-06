package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

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

    // Ripple animation state
    var rippleTrigger by remember { mutableStateOf(0) }
    var isAnimating by remember { mutableStateOf(false) }
    
    val rippleProgress by animateFloatAsState(
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = FastOutSlowInEasing
        ),
        label = "ripple_progress"
    )
    
    // Reset animation state after completion
    LaunchedEffect(rippleProgress, isAnimating) {
        if (rippleProgress >= 1f && isAnimating) {
            delay(50) // Small delay to ensure animation fully completes
            isAnimating = false
        }
    }

    Box(
        modifier = modifier
            .clickable {
                // Trigger ripple animation
                rippleTrigger++
                isAnimating = true
            },
        contentAlignment = Alignment.Center
    ) {
        // Ripple effect canvas (extends to full screen)
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * scale
            val maxScreenRadius = sqrt(
                (size.width / 2f).pow(2) + (size.height / 2f).pow(2)
            )
            
            // Draw ripple circles that extend beyond the sphere to screen edges
            if (rippleProgress > 0f) {
                val numRippleLayers = 40
                repeat(numRippleLayers) { layer ->
                    // Calculate radius from baseRadius to maxScreenRadius
                    val rippleRadius = baseRadius + (layer.toFloat() / numRippleLayers) * (maxScreenRadius - baseRadius)
                    
                    // Calculate ripple effect using helper function
                    val (rippleAlpha, strokeWidthMultiplier) = calculateExtendedRipple(
                        progress = rippleProgress,
                        radius = rippleRadius,
                        baseRadius = baseRadius,
                        maxRadius = maxScreenRadius
                    )
                    
                    if (rippleAlpha > 0.01f) {
                        drawCircle(
                            color = Color(0xFF4DFF88).copy(alpha = rippleAlpha),
                            radius = rippleRadius,
                            center = center,
                            style = Stroke(
                                width = (1.5f * strokeWidthMultiplier).dp.toPx()
                            )
                        )
                    }
                }
            }
        }
        
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
                val baseGlowAlpha = (0.3f - layer * 0.03f).coerceAtLeast(0.05f)
                
                // Apply ripple effect
                val layerPosition = normalizeLayerPosition(
                    layerIndex = layer,
                    totalLayers = 8,
                    startRadius = 1.08f,
                    endRadius = 1.64f
                )
                val rippleAlpha = calculateRippleAlpha(rippleProgress, layerPosition)
                val glowAlpha = (baseGlowAlpha * rippleAlpha).coerceIn(0f, 1f)
                
                val rippleWidth = calculateRippleStrokeWidth(rippleProgress, layerPosition)
                val strokeWidth = 2.dp.toPx() * rippleWidth
                
                drawCircle(
                    color = Color(0xFF4DFF88).copy(alpha = glowAlpha),
                    radius = glowRadius,
                    center = center,
                    style = Stroke(
                        width = strokeWidth
                    )
                )
            }

            // Main concentric circle layers (creating the layered sphere effect)
            val numLayers = 20
            repeat(numLayers) { layer ->
                val layerRadius = baseRadius * (0.3f + (layer.toFloat() / numLayers) * 0.7f)
                val baseLayerAlpha = when {
                    layer < numLayers / 4 -> 0.15f - (layer * 0.01f)
                    layer < numLayers / 2 -> 0.12f - ((layer - numLayers / 4) * 0.008f)
                    else -> 0.08f - ((layer - numLayers / 2) * 0.005f)
                }.coerceAtLeast(0.02f)
                
                // Apply ripple effect
                val layerPosition = normalizeLayerPosition(
                    layerIndex = layer,
                    totalLayers = numLayers,
                    startRadius = 0.3f,
                    endRadius = 1.0f
                )
                val rippleAlpha = calculateRippleAlpha(rippleProgress, layerPosition)
                val layerAlpha = (baseLayerAlpha * rippleAlpha).coerceIn(0f, 1f)
                
                val baseStrokeWidthPx = when {
                    layer % 3 == 0 -> 1.5f.dp.toPx()
                    layer % 3 == 1 -> 1f.dp.toPx()
                    else -> 0.8f.dp.toPx()
                }
                val rippleWidth = calculateRippleStrokeWidth(rippleProgress, layerPosition)
                val strokeWidthPx = baseStrokeWidthPx * rippleWidth
                
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
                val baseInnerAlpha = 0.2f - layer * 0.03f
                
                // Apply ripple effect
                val layerPosition = normalizeLayerPosition(
                    layerIndex = layer,
                    totalLayers = 5,
                    startRadius = 0.1f,
                    endRadius = 0.3f
                )
                val rippleAlpha = calculateRippleAlpha(rippleProgress, layerPosition)
                val innerAlpha = (baseInnerAlpha * rippleAlpha).coerceIn(0f, 1f)
                
                val rippleWidth = calculateRippleStrokeWidth(rippleProgress, layerPosition)
                val strokeWidth = 1.5f.dp.toPx() * rippleWidth
                
                drawCircle(
                    color = Color(0xFF4DFF88).copy(alpha = innerAlpha),
                    radius = innerRadius,
                    center = center,
                    style = Stroke(
                        width = strokeWidth
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

