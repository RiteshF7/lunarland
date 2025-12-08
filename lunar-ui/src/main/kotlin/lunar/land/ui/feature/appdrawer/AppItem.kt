package lunar.land.ui.feature.appdrawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A 3D-styled app button component with enhanced glow effects.
 * 
 * Features:
 * - Gradient background
 * - Enhanced 3D transform effects on hover/press
 * - Glow effect matching the app's color theme
 * - Smooth animations
 */
@Composable
fun AppItem(
    appData: AppItemData,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Use press state for touch interactions (works on both touch and pointer devices)
    val isInteracting = isPressed || isHovered
    
    // 3D transform animations matching HTML/CSS behavior
    // Default: rotateX(0deg), Hover: rotateX(-2deg) translateY(-4px), Active: rotateX(0deg) translateY(-1px)
    val rotationX by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> -2f  // Slight upward tilt on hover (matches HTML: -2deg)
            isPressed -> 0f  // No tilt on active/press (matches HTML: 0deg)
            else -> 0f  // Default no tilt (matches HTML: 0deg)
        },
        animationSpec = tween(200)  // Matches CSS: transition 0.2s
    )
    
    val rotationY by animateFloatAsState(
        targetValue = 0f  // No Y rotation in HTML
    )
    
    // Translation: hover moves up -4px, active moves up -1px
    val translationY by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> -4f  // Move up on hover (matches HTML: -4px)
            isPressed -> -1f  // Slight move up on active (matches HTML: -1px)
            else -> 0f  // Default no translation
        },
        animationSpec = tween(200)  // Matches CSS: transition 0.2s
    )
    
    val scale by animateFloatAsState(
        targetValue = 1f  // No scale in HTML, only transform
    )
    
    // Create gradient from bg color to darker version
    val gradientColors = listOf(
        appData.backgroundColor,
        Color(
            red = (appData.backgroundColor.red * 0.75f).coerceIn(0f, 1f),
            green = (appData.backgroundColor.green * 0.75f).coerceIn(0f, 1f),
            blue = (appData.backgroundColor.blue * 0.75f).coerceIn(0f, 1f),
            alpha = appData.backgroundColor.alpha
        )
    )
    
    // Enhanced shadow and glow based on state
    val shadowElevation = when {
        isHovered && !isPressed -> 12.dp
        isPressed -> 4.dp
        else -> 6.dp
    }
    
    val glowIntensity = when {
        isHovered && !isPressed -> 60.dp
        isPressed -> 35.dp
        else -> 45.dp
    }
    
    // Outer Box for glow effect
    Box(
        modifier = modifier
            .then(
                if (appData.isWide) {
                    Modifier
                        .fillMaxWidth(0.7f)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            .shadow(
                elevation = glowIntensity,
                shape = RoundedCornerShape(12.dp),
                ambientColor = appData.glowColor.copy(alpha = 0.7f),
                spotColor = appData.glowColor.copy(alpha = 0.9f)
            )
    ) {
        // Inner Box for content with 3D effects
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .graphicsLayer {
                    this.rotationX = rotationX
                    this.rotationY = rotationY
                    this.translationY = translationY.dp.toPx()
                    this.scaleX = scale
                    this.scaleY = scale
                }
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.linearGradient(
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f),
                        colors = gradientColors
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = appData.icon,
                    contentDescription = appData.name,
                    tint = appData.textColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appData.name,
                    color = appData.textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
