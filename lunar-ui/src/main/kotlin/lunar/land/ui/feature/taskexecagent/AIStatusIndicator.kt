package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * AI status indicator showing the current operational status.
 * Displays a simple green dot with glow effect and status text.
 * The dot spins for 3 seconds when the component is first displayed, then stops.
 */
@Composable
fun AIStatusIndicator(
    status: String,
    modifier: Modifier = Modifier
) {
    // Animation that runs for 3 seconds then stops
    val rotationAngle by animateFloatAsState(
        targetValue = 360f,
        animationSpec = tween(
            durationMillis = 3000,
            easing = LinearEasing
        ),
        label = "dot_rotation"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .drawBehind {
                    // Draw simple glow effect behind the dot
                    val glowRadius = size.minDimension * 1.5f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.High),
                        radius = glowRadius,
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(LunarTheme.AccentColor)
                .rotate(rotationAngle)
        )

        Spacer(modifier = Modifier.width(LunarTheme.Spacing.Medium))

        Text(
            text = "Status: $status",
            style = LunarTheme.Typography.BodyMedium,
            color = LunarTheme.TextPrimary
        )
    }
}

