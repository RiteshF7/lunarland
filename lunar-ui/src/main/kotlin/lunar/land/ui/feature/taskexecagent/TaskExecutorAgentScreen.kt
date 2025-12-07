package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R

/**
 * Manrope font family matching the HTML design.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

/**
 * Main screen composable for the AI Agent Interaction Sphere.
 * This is the entry point that orchestrates all the UI components.
 */
@Composable
fun TaskExecutorAgentScreen(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    aiStatus: String = "Online",
    onTextModeClick: () -> Unit = {}
) {
    // Background colors matching the HTML design
    val backgroundColor = Color(0xFF0a0f0a) // #0a0f0a
    val gradientStartColor = Color(0xFF0e1a10) // #0e1a10
    val accentColor = Color(0xFF4DFF88)
    
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
    
    // Animated particles background
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val particleOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_offset"
    )
    
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
        
        // Animated background particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val particleCount = 15
            repeat(particleCount) { i ->
                val angle = (i * 360f / particleCount + particleOffset * 360f) * kotlin.math.PI / 180f
                val radius = size.minDimension * 0.4f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val x = centerX + kotlin.math.cos(angle).toFloat() * radius
                val y = centerY + kotlin.math.sin(angle).toFloat() * radius
                
                drawCircle(
                    color = accentColor.copy(alpha = 0.15f),
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
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
                // Center sphere
                SphereVisualizer(
                    modifier = Modifier.size(280.dp),
                    isListening = isListening
                )
            }
            
            // Neural Network Status
            NeuralNetworkStatus(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )

            // Footer Section
            PageFooter(
                modifier = Modifier.fillMaxWidth(),
                aiStatus = aiStatus,
                onTextModeClick = onTextModeClick
            )
        }
    }
}



