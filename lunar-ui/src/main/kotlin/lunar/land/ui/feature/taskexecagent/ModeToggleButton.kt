package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * Toggle button component for switching between text and voice modes.
 * Features a modern, subtle design with smooth animations.
 */
@Composable
fun ModeToggleButton(
    isTextMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgressState = animateFloatAsState(
        targetValue = if (isTextMode) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "toggle_animation"
    )
    val animatedProgress = animatedProgressState.value
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LunarTheme.CornerRadius.Medium))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        if (isTextMode) LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Medium) else LunarTheme.InactiveBackgroundColor,
                        if (isTextMode) LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Low) else LunarTheme.InactiveBackgroundColor
                    )
                )
            )
            .border(
                width = LunarTheme.BorderWidth,
                color = if (isTextMode) LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.High) else LunarTheme.BorderColor,
                shape = RoundedCornerShape(LunarTheme.CornerRadius.Medium)
            )
            .clickable { onToggle() }
            .padding(horizontal = LunarTheme.Spacing.ExtraSmall, vertical = LunarTheme.Spacing.ExtraSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Mode Button
            ModeButton(
                text = "Voice",
                isActive = !isTextMode,
                modifier = Modifier.weight(1f),
                animatedProgress = 1f - animatedProgress
            )
            
            // Text Mode Button
            ModeButton(
                text = "Text",
                isActive = isTextMode,
                modifier = Modifier.weight(1f),
                animatedProgress = animatedProgress
            )
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    isActive: Boolean,
    animatedProgress: Float,
    modifier: Modifier = Modifier
) {
    val activeTextColor = LunarTheme.TextPrimary
    val inactiveTextColor = LunarTheme.TextSecondary
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LunarTheme.CornerRadius.Small))
            .background(
                color = if (isActive) {
                    LunarTheme.AccentColor.copy(alpha = 0.2f * animatedProgress)
                } else {
                    Color.Transparent
                }
            )
            .padding(vertical = 10.dp, horizontal = LunarTheme.Spacing.Large),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (isActive) {
                LunarTheme.Typography.BodyMedium.copy(fontWeight = FontWeight.SemiBold)
            } else {
                LunarTheme.Typography.BodyMedium
            },
            color = if (isActive) {
                Color(
                    red = inactiveTextColor.red + (activeTextColor.red - inactiveTextColor.red) * animatedProgress,
                    green = inactiveTextColor.green + (activeTextColor.green - inactiveTextColor.green) * animatedProgress,
                    blue = inactiveTextColor.blue + (activeTextColor.blue - inactiveTextColor.blue) * animatedProgress,
                    alpha = inactiveTextColor.alpha + (activeTextColor.alpha - inactiveTextColor.alpha) * animatedProgress
                )
            } else {
                inactiveTextColor
            }
        )
    }
}

