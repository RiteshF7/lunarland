package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lunar.land.ui.core.theme.LunarTheme

/**
 * Logs View Component
 * Displays task execution logs with scrolling
 */
@Composable
fun LogsView(
    outputText: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll to bottom when output changes
    LaunchedEffect(outputText) {
        if (outputText.isNotEmpty()) {
            kotlinx.coroutines.delay(50) // Small delay to ensure text is rendered
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LunarTheme.CornerRadius.Medium))
            .background(color = LunarTheme.InactiveBackgroundColor.copy(alpha = 0.6f))
            .border(
                width = LunarTheme.BorderWidth,
                color = LunarTheme.BorderColor,
                shape = RoundedCornerShape(LunarTheme.CornerRadius.Medium)
            )
    ) {
        if (outputText.isEmpty()) {
            // Show placeholder when no logs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No logs yet. Run a task to see output here.",
                    style = LunarTheme.Typography.BodySmall,
                    color = LunarTheme.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Show logs with proper scrolling
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Split output into lines for better rendering
                val lines = outputText.lines()
                lines.forEach { line ->
                    Text(
                        text = line.ifEmpty { " " }, // Empty lines show as space
                        style = LunarTheme.Typography.BodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = LunarTheme.TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

