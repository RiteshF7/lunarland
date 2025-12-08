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
    // Create a beautiful, MAXIMUM visibility gradient with depth
    // Radial gradient from top creates a natural light source effect
    // EXTREMELY noticeable color variations that complement the glowing app items
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    center = Offset(0.5f, 0.05f), // Top-center for natural light effect
                    radius = 1.0f, // Smaller radius for more concentrated, MAXIMUM visible gradient
                    colors = listOf(
                        Color(0xFF5a5a5a),  // MAXIMUM lighter at center - creates MAXIMUM visible depth
                        Color(0xFF4f4f4f),  // Very light grey
                        Color(0xFF454545),  // Light grey
                        Color(0xFF3a3a3a),  // Medium-light grey
                        Color(0xFF2f2f2f),  // Medium grey
                        Color(0xFF252525),  // Dark grey
                        Color(0xFF1a1a1a),  // Very dark
                        Color(0xFF0f0f0f),   // Almost black
                        Color(0xFF000000)    // Pure black at edges
                    )
                )
            )
    )
}

