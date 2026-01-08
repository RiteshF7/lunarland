package lunar.land.ui.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lunar.land.ui.core.theme.LunarTheme

/**
 * Reusable navigation button component matching AppItem.kt style.
 * Features clean, simple design with subtle interactions.
 */
@Composable
fun NavigationButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Use press state for touch interactions
    val isInteracting = isPressed || isHovered
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LunarTheme.CornerRadius.Small))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        if (isInteracting && enabled) {
                            LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Medium)
                        } else if (!enabled) {
                            LunarTheme.InactiveBackgroundColor.copy(alpha = 0.5f)
                        } else {
                            LunarTheme.InactiveBackgroundColor
                        },
                        if (isInteracting && enabled) {
                            LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Low)
                        } else if (!enabled) {
                            LunarTheme.InactiveBackgroundColor.copy(alpha = 0.5f)
                        } else {
                            LunarTheme.InactiveBackgroundColor
                        }
                    )
                )
            )
            .border(
                width = LunarTheme.BorderWidth,
                color = if (isInteracting && enabled) {
                    LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.High)
                } else if (!enabled) {
                    LunarTheme.BorderColor.copy(alpha = 0.5f)
                } else {
                    LunarTheme.BorderColor
                },
                shape = RoundedCornerShape(LunarTheme.CornerRadius.Small)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        // Content with padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = if (enabled) LunarTheme.TextPrimary else LunarTheme.TextDisabled,
                style = LunarTheme.Typography.BodyMedium
            )
        }
    }
}

