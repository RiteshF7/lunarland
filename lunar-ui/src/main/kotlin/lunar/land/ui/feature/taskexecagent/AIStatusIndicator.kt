package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lunar.land.ui.R

/**
 * Manrope font family matching the HTML design.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

/**
 * AI status indicator showing the current operational status.
 * Displays a pulsing green dot with glow effect and status text.
 */
@Composable
fun AIStatusIndicator(
    status: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(scale)
                .drawBehind {
                    // Draw glow effect behind the dot
                    val glowRadius = size.minDimension * 2.5f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = Color(0xFF4DFF88).copy(alpha = 0.4f),
                        radius = glowRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF4DFF88).copy(alpha = 0.6f),
                        radius = glowRadius * 0.6f,
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(Color(0xFF4DFF88))
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "AI Status: $status",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = manropeFontFamily
            ),
            color = Color.White
        )
    }
}

