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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),  // Pure white center
                        Color(0xFFF8FAFC),  // Very light gray-white
                        Color(0xFFF1F5F9),  // Light blue-gray
                        Color(0xFFE8F0F8),  // Soft blue-white
                        Color(0xFFE0EBF5)   // Light blue
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
