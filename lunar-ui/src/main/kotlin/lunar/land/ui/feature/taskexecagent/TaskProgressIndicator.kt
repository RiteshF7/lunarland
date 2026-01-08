package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lunar.land.ui.core.ui.AISphere
import lunar.land.ui.core.ui.AISphereConfig

/**
 * Rotating progress indicator using AISphere with white colors.
 * Shows when a task is running.
 */
@Composable
fun TaskProgressIndicator(
    modifier: Modifier = Modifier
) {
    // White color configuration for progress indicator
    val whiteConfig = AISphereConfig(
        brightGlowColor = Color.White,
        glowGreenColor = Color(0xFFFFFFFF),
        primaryGreenColor = Color(0xFFFFFFFF),
        lightGreenColor = Color(0xFFFFFFFF),
        whiteHighlightColor = Color.White,
        shadowColor = Color(0xFF333333),
        radiusMultiplier = 0.4f,
        glowLayerCount = 3,
        glowRadiusBase = 0.15f,
        rotationAngle = 0f, // Will be animated
        gradientRadiusMultiplier = 1f,
        bodyGradientAlphas = listOf(0.15f, 0.25f, 0.35f, 0.5f),
        shadowGradientAlphas = listOf(0.4f, 0.6f, 0.8f),
        borderAlpha = 0.7f,
        borderGradientAlphas = listOf(0.8f, 0.9f, 1f, 0.9f, 0.8f),
        highlightRadiusMultiplier = 1f,
        highlightAlphas = listOf(0.4f, 0.3f, 0.15f),
        secondaryHighlightRadiusMultiplier = 0.4f,
        secondaryHighlightAlphas = listOf(0.25f, 0.2f)
    )
    
    // Infinite rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "progress_rotation")
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Update config with animated rotation (convert degrees to radians)
    val animatedConfig = whiteConfig.copy(rotationAngle = rotationAngle * (kotlin.math.PI.toFloat() / 180f))
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AISphere(
            size = 16.dp,
            config = animatedConfig,
            modifier = Modifier.size(16.dp)
        )
    }
}

