package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
    onTextModeClick: () -> Unit = {},
    onExecuteCommand: (String) -> Unit = {}
) {
    var isTextMode by remember { mutableStateOf(false) }
    var isTextInputFocused by remember { mutableStateOf(false) }
    
    // Use theme colors and make them darker
    val colorScheme = MaterialTheme.colorScheme
    
    // Create darker background by blending theme background with black
    val backgroundColor = Color(
        red = (colorScheme.background.red * 0.3f + 0.05f).coerceIn(0f, 1f),
        green = (colorScheme.background.green * 0.3f + 0.05f).coerceIn(0f, 1f),
        blue = (colorScheme.background.blue * 0.3f + 0.05f).coerceIn(0f, 1f)
    )
    
    // Create darker gradient start color
    val gradientStartColor = Color(
        red = (colorScheme.surface.red * 0.4f + 0.08f).coerceIn(0f, 1f),
        green = (colorScheme.surface.green * 0.4f + 0.08f).coerceIn(0f, 1f),
        blue = (colorScheme.surface.blue * 0.4f + 0.08f).coerceIn(0f, 1f)
    )
    
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section with Mode Toggle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PageHeader(
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Mode Toggle Button
                ModeToggleButton(
                    isTextMode = isTextMode,
                    onToggle = { 
                        isTextMode = !isTextMode
                        // Reset focus state when switching modes
                        isTextInputFocused = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isTextMode) {
                    // Text mode: Show sphere when not typing, hide when typing
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Show sphere when text input is not focused (to show agent state)
                        AnimatedVisibility(
                            visible = !isTextInputFocused,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut() + scaleOut(targetScale = 0.8f),
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            SphereVisualizer(
                                modifier = Modifier.size(200.dp),
                                isListening = isListening
                            )
                        }
                        
                        // Text Input Panel
                        TextInputPanel(
                            onExecute = { command ->
                                onExecuteCommand(command)
                            },
                            onFocusChange = { focused ->
                                isTextInputFocused = focused
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                    }
                } else {
                    // Voice mode: Always show sphere
                    SphereVisualizer(
                        modifier = Modifier.size(280.dp),
                        isListening = isListening
                    )
                }
            }

            // Footer Section
            PageFooter(
                modifier = Modifier.fillMaxWidth(),
                aiStatus = aiStatus,
                isTextMode = isTextMode,
                onTextModeClick = onTextModeClick
            )
        }
    }
}



