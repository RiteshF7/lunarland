package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/**
 * Main screen composable for the AI Agent Interaction Sphere.
 * This is the entry point that orchestrates all the UI components.
 */
@Composable
fun TaskExecutorAgentScreen(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    aiStatus: String = "Ready",
    onSpeakClick: () -> Unit = {}
) {
    // Background colors matching the HTML design
    val backgroundColor = Color(0xFF0a0f0a) // #0a0f0a
    val gradientStartColor = Color(0xFF0e1a10) // #0e1a10
    
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    
    val gradientBrush = remember(size) {
        if (size.width > 0 && size.height > 0) {
            Brush.radialGradient(
                colors = listOf(
                    gradientStartColor,
                    backgroundColor
                ),
                center = Offset(size.width / 2, size.height / 2),
                radius = kotlin.math.max(size.width, size.height) * 0.6f
            )
        } else {
            Brush.radialGradient(
                colors = listOf(gradientStartColor, backgroundColor),
                center = Offset.Zero,
                radius = 2000f
            )
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Radial gradient overlay - centered
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { newSize ->
                    size = androidx.compose.ui.geometry.Size(newSize.width.toFloat(), newSize.height.toFloat())
                }
                .background(brush = gradientBrush)
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
        // Header Section
        PageHeader(
            modifier = Modifier.fillMaxWidth()
        )

        // Main Content - Sphere Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SphereVisualizer(
                modifier = Modifier.size(300.dp),
                isListening = isListening
            )
        }

        // Footer Section
        PageFooter(
            modifier = Modifier.fillMaxWidth(),
            aiStatus = aiStatus,
            onSpeakClick = onSpeakClick
        )
        }
    }
}

