package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Modern container for the app drawer with vibrant gradient background.
 * 
 * @param modifier Modifier for styling and layout
 * @param content The app drawer content
 */
@Composable
fun AppDrawerContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Dark black background with subtle grey gradients
    val darkBlack = Color(0xFF0a0a0a)  // Very dark black
    val darkGrey1 = Color(0xFF1a1a1a)  // Slightly lighter grey
    val darkGrey2 = Color(0xFF151515)  // Medium dark grey
    val darkGrey3 = Color(0xFF0f0f0f)  // Dark grey
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        darkGrey1,      // Slightly lighter center
                        darkGrey2,      // Medium grey
                        darkGrey3,      // Dark grey
                        darkBlack       // Very dark black edges
                    ),
                    center = Offset(500f, 300f),
                    radius = 1200f
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
