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

/**
 * Manrope font family matching the HTML design.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

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
    val accentColor = Color(0xFF4DFF88)
    val backgroundColor = Color(0xFF0a0f0a)
    val inactiveBackgroundColor = Color(0xFF1a1f1a)
    val borderColor = Color(0xFF2a3a2a)
    
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        if (isTextMode) accentColor.copy(alpha = 0.15f) else inactiveBackgroundColor,
                        if (isTextMode) accentColor.copy(alpha = 0.08f) else inactiveBackgroundColor
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isTextMode) accentColor.copy(alpha = 0.3f) else borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
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
    val accentColor = Color(0xFF4DFF88)
    val backgroundColor = Color(0xFF0a0f0a)
    val activeTextColor = Color.White
    val inactiveTextColor = Color.White.copy(alpha = 0.6f)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = if (isActive) {
                    accentColor.copy(alpha = 0.2f * animatedProgress)
                } else {
                    Color.Transparent
                }
            )
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = manropeFontFamily,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
            ),
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

