package lunar.land.ui.feature.homescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged

/**
 * Background component with radial gradient overlay matching TaskExecutorAgentScreen design.
 */
@Composable
fun HomeScreenBackground(
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    
    val gradientBrush = remember(size) {
        if (size.width > 0 && size.height > 0) {
            Brush.radialGradient(
                colors = listOf(
                    HomeScreenTheme.gradientStartColor,
                    HomeScreenTheme.backgroundColor
                ),
                center = Offset(size.width / 2, size.height / 2),
                radius = kotlin.math.max(size.width, size.height) * 0.6f
            )
        } else {
            Brush.radialGradient(
                colors = listOf(
                    HomeScreenTheme.gradientStartColor,
                    HomeScreenTheme.backgroundColor
                ),
                center = Offset.Zero,
                radius = 2000f
            )
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HomeScreenTheme.backgroundColor)
    ) {
        // Radial gradient overlay - centered
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { newSize ->
                    size = androidx.compose.ui.geometry.Size(
                        newSize.width.toFloat(),
                        newSize.height.toFloat()
                    )
                }
                .background(brush = gradientBrush)
        )
    }
}

