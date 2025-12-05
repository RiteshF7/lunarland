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

@Composable
fun AISphere(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val sizePx = with(density) { size.toPx() }
                drawAISphere(size = sizePx)
            },
        contentAlignment = Alignment.Center
    ) {
        // Empty content - sphere is drawn in drawBehind
    }
}

private fun DrawScope.drawAISphere(
    size: Float
) {
    val center = Offset(size / 2f, size / 2f)
    val radius = size * 0.4f
    
    // Draw static glowing green sphere
    drawGlowingSphere(
        center = center,
        radius = radius
    )
}

private fun DrawScope.drawGlowingSphere(
    center: Offset,
    radius: Float
) {
    // Green color palette - only bright glowing greens (no dark colors)
    val brightGlow = Color(0xFF00FFCC)    // Brightest glow
    val glowGreen = Color(0xFF00FF99)     // Glowing green
    val primaryGreen = Color(0xFF00FFAA)  // Bright green (brighter)
    val lightGreen = Color(0xFF66FFCC)    // Lighter green (brighter)
    
    // Draw extensive outer glow layers for stronger glow effect
    val glowLayers = 8
    repeat(glowLayers) { layer ->
        val glowRadius = radius * (1f + (layer + 1) * 0.2f)
        val glowAlpha = (0.25f - layer * 0.02f).coerceAtLeast(0.05f)
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGreen.copy(alpha = glowAlpha),
                    glowGreen.copy(alpha = glowAlpha * 0.7f),
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
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,                  // Fully transparent center (no background)
                brightGlow.copy(alpha = 0.2f),      // Very light glow
                glowGreen.copy(alpha = 0.3f),       // Glowing green
                lightGreen.copy(alpha = 0.35f),     // Light middle
                primaryGreen.copy(alpha = 0.4f),     // Medium glow
                Color.Transparent                   // Fully transparent outside
            ),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
            radius = radius * 1.5f
        ),
        radius = radius,
        center = center
    )
    
    // Draw sphere border with glowing green
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                brightGlow.copy(alpha = 1f),
                glowGreen.copy(alpha = 0.95f),
                primaryGreen.copy(alpha = 1f),
                lightGreen.copy(alpha = 0.95f),
                primaryGreen.copy(alpha = 1f),
                glowGreen.copy(alpha = 0.95f),
                brightGlow.copy(alpha = 1f)
            ),
            center = center
        ),
        radius = radius,
        center = center,
        alpha = 0.85f
    )
    
    // Draw inner 3D highlight for depth (top-left light source)
    val highlightRadius = radius * 0.65f
    val highlightOffset = Offset(
        center.x - radius * 0.3f,
        center.y - radius * 0.3f
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                brightGlow.copy(alpha = 0.5f),
                lightGreen.copy(alpha = 0.4f),
                primaryGreen.copy(alpha = 0.2f),
                Color.Transparent
            ),
            center = highlightOffset,
            radius = highlightRadius
        ),
        radius = highlightRadius,
        center = highlightOffset
    )
    
    // Draw secondary highlight for more 3D effect
    val secondaryHighlightRadius = radius * 0.4f
    val secondaryHighlightOffset = Offset(
        center.x - radius * 0.2f,
        center.y - radius * 0.2f
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.3f),
                brightGlow.copy(alpha = 0.25f),
                Color.Transparent
            ),
            center = secondaryHighlightOffset,
            radius = secondaryHighlightRadius
        ),
        radius = secondaryHighlightRadius,
        center = secondaryHighlightOffset
    )
}


