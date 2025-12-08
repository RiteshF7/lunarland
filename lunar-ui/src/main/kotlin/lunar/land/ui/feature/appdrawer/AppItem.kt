package lunar.land.ui.feature.appdrawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun AppItemBorderGlowBox(
    modifier: Modifier = Modifier,
    isInteracting: Boolean,
    borderColor: Color = Color.White,
    glowColor: Color = Color.Cyan,
    borderWidth: Float = 2f,
    cornerRadius: Float = 22f,
    content: @Composable BoxScope.() -> Unit
) {
    // Animate glow alpha for glowing border
    val glowAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 0.55f else 0.22f,
        animationSpec = tween(160)
    )

    // Compose border and glow using multiple overlays
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isInteracting) 16.dp else 7.dp,
                shape = RoundedCornerShape(cornerRadius.dp),
                ambientColor = glowColor.copy(alpha = glowAlpha),
                spotColor = glowColor.copy(alpha = glowAlpha)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isInteracting) 0.18f else 0.12f),
                        glowColor.copy(alpha = if (isInteracting) 0.14f else 0.10f),
                        Color.Black.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
            .clip(RoundedCornerShape(cornerRadius.dp))
    ) {
        // Border layer (placed inside the Box for correct clipping)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(Color.Transparent)
                .border(
                    width = borderWidth.dp,
                    color = borderColor.copy(alpha = if (isInteracting) 1.0f else 0.6f),
                    shape = RoundedCornerShape(cornerRadius.dp)
                )
        )

        content()
    }
}

/**
 * Applies a 3D transform and glow effect to a composable based on hover/pressed state.
 * Usage: Wrap this around your AppItem content to get the "3D card" effect.
 */
@Composable
fun AppItem3DEffectBox(
    modifier: Modifier = Modifier,
    isInteracting: Boolean,
    rotationX: Float,
    rotationY: Float,
    translationY: Float,
    cameraDistancePx: Float,
    glowColor: Color,
    content: @Composable BoxScope.() -> Unit
) {
    // Animate glow alpha (stronger when interacting)
    val glowAlpha by animateFloatAsState(targetValue = if (isInteracting) 0.35f else 0.14f, animationSpec = tween(160))

    Box(
        modifier = modifier
            .graphicsLayer {
                // 3D like transforms
                this.cameraDistance = cameraDistancePx
                this.transformOrigin = TransformOrigin(0.5f, 1f) // Pivot along bottom center (like CSS)
                this.rotationX = rotationX
                this.rotationY = rotationY
                this.translationY = translationY
            }
            .shadow(
                elevation = if (isInteracting) 12.dp else 5.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = glowColor.copy(alpha = glowAlpha),
                spotColor = glowColor.copy(alpha = glowAlpha)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isInteracting) 0.14f else 0.11f),
                        glowColor.copy(alpha = 0.12f),
                        Color.Black.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
    ) {
        content()
    }
}


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
    val density = LocalDensity.current
    
    // Enhanced 3D perspective - closer camera for more pronounced 3D effect
    // Using 600dp instead of 1000dp for stronger perspective
    val cameraDistance = with(density) { 600.dp.toPx() }
    
    // Use press state for touch interactions (works on both touch and pointer devices)
    val isInteracting = isPressed || isHovered
    
    // Enhanced 3D transform animations with more pronounced effects
    val rotationX by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> -4f  // More pronounced upward tilt on hover
            isPressed -> -1f  // Slight tilt on press
            else -> -1f  // Default slight upward tilt for 3D effect
        },
        animationSpec = tween(200)
    )
    
    val rotationY by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 1.5f  // Slight perspective rotation on hover
            isPressed -> 0.5f  // Minimal perspective on press
            else -> 0.5f  // Default slight perspective for depth
        },
        animationSpec = tween(200)
    )
    
    // Enhanced translation for more pronounced 3D lift effect
    val translationY by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> -8f  // More lift on hover
            isPressed -> -2f  // Slight lift on press
            else -> -1f  // Default slight lift for 3D depth
        },
        animationSpec = tween(200)
    )
    
    val translationZ by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 8f  // Move forward in 3D space
            isPressed -> 2f  // Slight forward movement
            else -> 0f  // Default no Z translation
        },
        animationSpec = tween(200)
    )
    
    val scale by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 1.03f  // Slight scale up on hover
            isPressed -> 0.98f  // Slight scale down on press
            else -> 1f
        },
        animationSpec = tween(200)
    )
    
    // Enhanced gradient with multiple stops for better 3D depth effect
    val baseColor = appData.backgroundColor
    val darkerColor = Color(
        red = (baseColor.red * 0.7f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.7f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.7f).coerceIn(0f, 1f),
        alpha = baseColor.alpha
    )
    val darkestColor = Color(
        red = (baseColor.red * 0.5f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.5f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.5f).coerceIn(0f, 1f),
        alpha = baseColor.alpha
    )
    
    // Multi-stop gradient for 3D lighting effect
    val gradientColors = listOf(
        baseColor.copy(alpha = baseColor.alpha * 1.1f),  // Top highlight
        baseColor,  // Main color
        darkerColor,  // Mid shadow
        darkestColor  // Bottom shadow
    )
    
    // Enhanced shadow and glow with multiple layers for 3D depth
    val shadowElevation = when {
        isHovered && !isPressed -> 16.dp
        isPressed -> 6.dp
        else -> 8.dp
    }
    
    val glowIntensity = when {
        isHovered && !isPressed -> 80.dp
        isPressed -> 45.dp
        else -> 55.dp
    }
    
    val innerShadowElevation = when {
        isHovered && !isPressed -> 4.dp
        isPressed -> 2.dp
        else -> 3.dp
    }
    
    // Animated border alpha based on interaction state
    val borderAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1.0f else 0.7f,
        animationSpec = tween(200)
    )
    
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
                    // Enhanced 3D perspective for more pronounced effect
                    this.cameraDistance = cameraDistance
                    
                    // Transform origin at bottom center for card-like 3D rotation
                    this.transformOrigin = TransformOrigin(0.5f, 1f)
                    
                    // Apply enhanced 3D transforms
                    this.rotationX = rotationX
                    this.rotationY = rotationY
                    this.translationY = translationY.dp.toPx()
                    // Use scale to simulate Z depth (forward movement in 3D space)
                    val zScale = 1f + (translationZ / 100f)
                    this.scaleX = scale * zScale
                    this.scaleY = scale * zScale
                    
                    // Enhanced depth effect with rotation-based alpha
                    val rotationFactor = kotlin.math.abs(rotationX) / 90f
                    this.alpha = 1f - rotationFactor * 0.15f
                }
                // Outer glow shadow layer
                .shadow(
                    elevation = glowIntensity,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = appData.glowColor.copy(alpha = 0.6f),
                    spotColor = appData.glowColor.copy(alpha = 0.8f)
                )
                // Main depth shadow
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.Black.copy(alpha = 0.6f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                // Inner shadow for depth
                .shadow(
                    elevation = innerShadowElevation,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(12.dp))
                // Enhanced multi-stop gradient for 3D lighting
                .background(
                    brush = Brush.linearGradient(
                        start = Offset(0f, 0f),
                        end = Offset(0f, 1000f),
                        colors = gradientColors
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = appData.glowColor.copy(alpha = borderAlpha),
                    shape = RoundedCornerShape(12.dp)
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
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = appData.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
