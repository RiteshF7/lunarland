package lunar.land.ui.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.cos

/**
 * Configuration for the AISphere appearance.
 * All UI properties can be adjusted from here.
 */
data class AISphereConfig(
    // Colors
    val brightGlowColor: Color = Color(0xFF00FFCC),
    val glowGreenColor: Color = Color(0xFF00FF99),
    val primaryGreenColor: Color = Color(0xFF00FFAA),
    val lightGreenColor: Color = Color(0xFF66FFCC),
    val whiteHighlightColor: Color = Color.White,
    val shadowColor: Color = Color(0xFF003322),

    // Sphere size
    val radiusMultiplier: Float = 0.4f,

    // Glow layers
    val glowLayerCount: Int = 4,
    val glowRadiusBase: Float = 0.1f,

    // Gradient center (animated)
    var rotationAngle: Float = 0f,
    val gradientRadiusMultiplier: Float = 1f,

    // Body gradient
    val bodyGradientAlphas: List<Float> = listOf(0.2f, 0.3f, 0.35f, 0.4f),
    val shadowGradientAlphas: List<Float> = listOf(0.6f, 0.8f, 1f),

    // Border
    val borderAlpha: Float = 0.85f,
    val borderGradientAlphas: List<Float> = listOf(1f, 0.95f, 1f, 0.95f, 1f),

    // Highlights (animated)
    val highlightRadiusMultiplier: Float = 1f,
    val highlightAlphas: List<Float> = listOf(0.5f, 0.4f, 0.2f),
    val secondaryHighlightRadiusMultiplier: Float = 0.4f,
    val secondaryHighlightAlphas: List<Float> = listOf(0.3f, 0.25f)
) {
    fun animatedOffsets(): Pair<Float, Float> {
        return Pair(
            sin(rotationAngle) * 0.3f,
            cos(rotationAngle) * 0.3f
        )
    }
}

// Default configuration instance
private val defaultAISphereConfig = AISphereConfig()

@Composable
fun AISphere(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    config: AISphereConfig = defaultAISphereConfig
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val sizePx = with(density) { size.toPx() }
                drawAISphere(size = sizePx, config = config)
            },
        contentAlignment = Alignment.Center
    ) {
        // Empty content - sphere is drawn in drawBehind
    }
}

private fun DrawScope.drawAISphere(
    size: Float,
    config: AISphereConfig
) {
    val center = Offset(size / 2f, size / 2f)
    val radius = size * config.radiusMultiplier
    
    // Draw static glowing green sphere
    drawGlowingSphere(
        center = center,
        radius = radius,
        config = config
    )
}

private fun DrawScope.drawGlowingSphere(
    center: Offset,
    radius: Float,
    config: AISphereConfig
) {
    // Draw extensive outer glow layers for stronger glow effect
    repeat(config.glowLayerCount) { layer ->
        val glowRadius = radius * (1f + (layer + 1) * config.glowRadiusBase)
        val glowAlpha = (0.25f - layer * 0.02f).coerceAtLeast(0.05f)
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    config.primaryGreenColor.copy(alpha = glowAlpha),
                    config.glowGreenColor.copy(alpha = glowAlpha * 0.7f),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )
    }
    
    // Draw main sphere body - perfectly round circle with transparent center
    val bodyGradientColors = mutableListOf<Color>().apply {
        add(Color.Transparent) // Fully transparent center
        add(config.brightGlowColor.copy(alpha = config.bodyGradientAlphas[0]))
        add(config.glowGreenColor.copy(alpha = config.bodyGradientAlphas[1]))
        add(config.lightGreenColor.copy(alpha = config.bodyGradientAlphas[2]))
        add(config.primaryGreenColor.copy(alpha = config.bodyGradientAlphas[3]))
        add(Color.Transparent) // Fully transparent outside
    }
    
    val (offsetX, offsetY) = config.animatedOffsets()
    drawCircle(
        brush = Brush.radialGradient(
            colors = bodyGradientColors,
            center = Offset(
                center.x - radius * offsetX,
                center.y - radius * offsetY
            ),
            radius = radius * config.gradientRadiusMultiplier
        ),
        radius = radius,
        center = center
    )
    
    // Draw sphere border with glowing green
    val borderGradientColors = listOf(
        config.brightGlowColor.copy(alpha = config.borderGradientAlphas[0]),
        config.glowGreenColor.copy(alpha = config.borderGradientAlphas[1]),
        config.primaryGreenColor.copy(alpha = config.borderGradientAlphas[2]),
        config.lightGreenColor.copy(alpha = config.borderGradientAlphas[3]),
        config.brightGlowColor.copy(alpha = config.borderGradientAlphas[4])
    )
    
    drawCircle(
        brush = Brush.sweepGradient(
            colors = borderGradientColors,
            center = center
        ),
        radius = radius,
        center = center,
        alpha = config.borderAlpha
    )
    
    // Draw inner 3D highlight for depth (top-left light source)
    val (highlightOffsetX, highlightOffsetY) = config.animatedOffsets()
    val highlightRadius = radius * config.highlightRadiusMultiplier
    val highlightOffset = Offset(
        center.x - radius * highlightOffsetX,
        center.y - radius * highlightOffsetY
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                config.brightGlowColor.copy(alpha = config.highlightAlphas[0]),
                config.lightGreenColor.copy(alpha = config.highlightAlphas[1]),
                config.primaryGreenColor.copy(alpha = config.highlightAlphas[2]),
                Color.Transparent
            ),
            center = highlightOffset,
            radius = highlightRadius
        ),
        radius = highlightRadius,
        center = highlightOffset
    )
    
    // Draw secondary highlight for more 3D effect
    val (secondaryOffsetX, secondaryOffsetY) = config.animatedOffsets()
    val secondaryHighlightRadius = radius * config.secondaryHighlightRadiusMultiplier
    val secondaryHighlightOffset = Offset(
        center.x - radius * secondaryOffsetX * 0.67f,
        center.y - radius * secondaryOffsetY * 0.67f
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                config.whiteHighlightColor.copy(alpha = config.secondaryHighlightAlphas[0]),
                config.brightGlowColor.copy(alpha = config.secondaryHighlightAlphas[1]),
                Color.Transparent
            ),
            center = secondaryHighlightOffset,
            radius = secondaryHighlightRadius
        ),
        radius = secondaryHighlightRadius,
        center = secondaryHighlightOffset
    )
}


