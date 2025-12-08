package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Modern container for the app drawer with beautiful gradient background matching home screen.
 * 
 * @param modifier Modifier for styling and layout
 * @param content The app drawer content
 */
@Composable
fun AppDrawerContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Beautiful gradient background matching home screen
    Box(
        modifier = Modifier
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
            .then(modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            content()
        }
    }
}
