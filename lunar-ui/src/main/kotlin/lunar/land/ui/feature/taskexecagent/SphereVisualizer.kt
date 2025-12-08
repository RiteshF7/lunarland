package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme
import com.termux.app.taskexecutor.model.TaskStatus

/**
 * The glowing sphere visualizer with texture and pulse animation.
 * This is the central interactive element of the UI.
 * Supports different visual states based on task status.
 */
@Composable
fun SphereVisualizer(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    taskStatus: TaskStatus = TaskStatus.STOPPED,
    isTaskRunning: Boolean = false,
    onSphereClick: (() -> Unit)? = null,
    onStopTask: (() -> Unit)? = null
) {
    // Determine sphere color based on task status
    val sphereColor = when (taskStatus) {
        TaskStatus.ERROR -> Color(0xFF, 0x33, 0x33) // Red for error
        TaskStatus.SUCCESS -> LunarTheme.AccentColor // Default accent for success
        TaskStatus.RUNNING -> LunarTheme.AccentColor // Default accent for running
        TaskStatus.STOPPED -> LunarTheme.AccentColor // Default accent for idle
    }
    
    // Animation state: only animate when running, not on idle/stopped
    val shouldAnimate = isTaskRunning || taskStatus == TaskStatus.RUNNING
    
    val infiniteTransition = rememberInfiniteTransition(label = "sphere_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldAnimate) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Rotating dots animation
    val dotRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dot_rotation"
    )

    // Ripple animation state - trigger on click or when task starts running
    var rippleTrigger by remember { mutableStateOf(0) }
    var isAnimating by remember { mutableStateOf(false) }
    
    // Auto-start ripple when task starts running
    LaunchedEffect(isTaskRunning, taskStatus) {
        if (isTaskRunning && taskStatus == TaskStatus.RUNNING) {
            rippleTrigger++
            isAnimating = true
        } else if (taskStatus == TaskStatus.SUCCESS || taskStatus == TaskStatus.ERROR) {
            // Stop animation on success/error
            isAnimating = false
        }
    }
    
    val rippleProgress by animateFloatAsState(
        targetValue = if (isAnimating && shouldAnimate) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = FastOutSlowInEasing
        ),
        label = "ripple_progress"
    )
    
    // Reset animation state after completion (only if not running)
    LaunchedEffect(rippleProgress, isAnimating, shouldAnimate) {
        if (rippleProgress >= 1f && isAnimating && !shouldAnimate) {
            delay(50) // Small delay to ensure animation fully completes
            isAnimating = false
        }
    }

    Box(
        modifier = modifier
            .then(
                if (onSphereClick != null && !isTaskRunning) {
                    Modifier.clickable {
                        // Trigger ripple animation on click (only if not running)
                        rippleTrigger++
                        if (shouldAnimate) {
                            isAnimating = true
                        }
                        onSphereClick()
                    }
                } else {
                    Modifier
                }
            ),
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
                            color = sphereColor.copy(alpha = rippleAlpha),
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
                    color = sphereColor.copy(alpha = glowAlpha),
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
                    color = sphereColor.copy(alpha = layerAlpha),
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
                    color = sphereColor.copy(alpha = innerAlpha),
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
                        sphereColor.copy(alpha = 0.25f),
                        sphereColor.copy(alpha = LunarTheme.Alpha.Medium),
                        sphereColor.copy(alpha = LunarTheme.Alpha.VeryLow),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )
        }
        
        // Rotating dots around the sphere
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * scale
            val dotOrbitRadius = baseRadius * 1.05f // Dots orbit closer to the sphere
            val dotCount = 12 // Number of rotating dots
            val dotRadius = 2.5.dp.toPx()
            
            repeat(dotCount) { i ->
                // Calculate angle for each dot with rotation
                val baseAngle = (i * 360f / dotCount) * (kotlin.math.PI / 180f)
                val rotationAngle = (dotRotation * kotlin.math.PI / 180f)
                val angle = baseAngle + rotationAngle
                
                // Calculate dot position
                val dotX = center.x + cos(angle).toFloat() * dotOrbitRadius
                val dotY = center.y + sin(angle).toFloat() * dotOrbitRadius
                
                // Vary opacity based on position (fade effect)
                val distanceFromTop = (dotY - (center.y - dotOrbitRadius)) / (dotOrbitRadius * 2f)
                val opacity = 0.3f + (distanceFromTop * 0.7f).coerceIn(0f, 1f)
                
                // Draw dot with glow - only show dots when running
                if (shouldAnimate) {
                    drawCircle(
                        color = sphereColor.copy(alpha = opacity * 0.4f),
                        radius = dotRadius * 1.8f,
                        center = Offset(dotX, dotY)
                    )
                    drawCircle(
                        color = sphereColor.copy(alpha = opacity),
                        radius = dotRadius,
                        center = Offset(dotX, dotY)
                    )
                }
            }
        }

        // Inner content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isTaskRunning && onStopTask != null) {
                // Stop button inside sphere when task is running
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            color = Color(0xFF, 0x33, 0x33).copy(alpha = 0.95f),
                            shape = CircleShape
                        )
                        .clickable(
                            onClick = onStopTask,
                            enabled = true
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Stop Task",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (isListening) {
                LoadingIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                StatusText(text = "Listening...")
            }
        }
    }
}
