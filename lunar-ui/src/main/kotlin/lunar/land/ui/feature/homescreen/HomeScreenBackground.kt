package lunar.land.ui.feature.homescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Background component with beautiful gradient that complements the app items.
 * Creates a subtle, elegant dark gradient that matches the glowing UI elements.
 * Uses a combination of radial and subtle color variations for depth.
 */
@Composable
fun HomeScreenBackground(
    modifier: Modifier = Modifier
) {
    // Create a beautiful, clearly visible gradient with depth
    // Radial gradient from top creates a natural light source effect
    // Noticeable color variations that complement the glowing app items
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    center = Offset(0.5f, 0.15f), // Top-center for natural light effect
                    radius = 1.3f, // Medium radius for clearly visible gradient
                    colors = listOf(
                        Color(0xFF252525),  // Clearly lighter at center - creates visible depth
                        Color(0xFF1f1f1f),  // Dark grey
                        Color(0xFF181818),  // Darker grey
                        Color(0xFF121212),  // Very dark
                        Color(0xFF0a0a0a),   // Almost black
                        Color(0xFF000000)    // Pure black at edges
                    )
                )
            )
    )
}

