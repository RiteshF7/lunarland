package lunar.land.ui.feature.appdrawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

/**
 * Converts a Drawable to a Painter for use in Compose
 */
@Composable
private fun rememberDrawablePainter(drawable: Drawable?): Painter? {
    return remember(drawable) {
        drawable?.let {
            val bitmap = Bitmap.createBitmap(
                it.intrinsicWidth.coerceAtLeast(1),
                it.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            BitmapPainter(bitmap.asImageBitmap())
        }
    }
}

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
    
    // Get theme colors
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    // Enhanced 3D perspective - much closer camera for dramatic 3D effect
    // Using 400dp for very strong perspective and depth
    val cameraDistance = with(density) { 400.dp.toPx() }
    
    // Use press state for touch interactions (works on both touch and pointer devices)
    val isInteracting = isPressed || isHovered
    
    // Strong 3D animations for dramatic effect
    val rotationX by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> -8f  // Strong tilt for 3D card effect
            isPressed -> -2f  // Slight tilt on press
            else -> 0f  // Flat by default
        },
        animationSpec = tween(300)
    )
    
    val rotationY by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 2f  // Slight Y rotation for depth
            isPressed -> 0f
            else -> 0f
        },
        animationSpec = tween(300)
    )
    
    // Strong lift animation for 3D pop-out effect
    val translationY by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> -12f  // Strong lift on hover
            isPressed -> -3f  // Lift on press
            else -> 0f  // No translation by default
        },
        animationSpec = tween(300)
    )
    
    val translationZ by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 20f  // Strong forward movement in 3D space
            isPressed -> 5f  // Forward movement on press
            else -> 0f  // Default no Z translation
        },
        animationSpec = tween(250)
    )
    
    val scale by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 1.08f  // Strong scale for 3D pop
            isPressed -> 0.95f  // Scale down on press
            else -> 1f
        },
        animationSpec = tween(300)
    )
    
    // Use theme colors with app color blend
    val baseColor = appData.backgroundColor
    val glowColor = appData.glowColor
    
    // Create theme-aware gradient using surface colors
    val topHighlight = colorScheme.surface.copy(alpha = if (isInteracting) 0.6f else 0.4f)
    val lightColor = Color(
        red = (baseColor.red * 0.7f + colorScheme.surfaceVariant.red * 0.3f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.7f + colorScheme.surfaceVariant.green * 0.3f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.7f + colorScheme.surfaceVariant.blue * 0.3f).coerceIn(0f, 1f),
        alpha = if (isInteracting) 0.4f else 0.3f
    )
    val midColor = Color(
        red = (baseColor.red * 0.6f + colorScheme.primaryContainer.red * 0.4f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.6f + colorScheme.primaryContainer.green * 0.4f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.6f + colorScheme.primaryContainer.blue * 0.4f).coerceIn(0f, 1f),
        alpha = if (isInteracting) 0.35f else 0.25f
    )
    val accentColor = Color(
        red = (baseColor.red * 0.5f + colorScheme.primary.red * 0.5f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.5f + colorScheme.primary.green * 0.5f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.5f + colorScheme.primary.blue * 0.5f).coerceIn(0f, 1f),
        alpha = if (isInteracting) 0.25f else 0.2f
    )
    
    // Add glow color to gradient with theme integration
    val glowTint = Color(
        red = (glowColor.red * 0.7f + colorScheme.primary.red * 0.3f).coerceIn(0f, 1f),
        green = (glowColor.green * 0.7f + colorScheme.primary.green * 0.3f).coerceIn(0f, 1f),
        blue = (glowColor.blue * 0.7f + colorScheme.primary.blue * 0.3f).coerceIn(0f, 1f),
        alpha = if (isInteracting) 0.2f else 0.12f
    )
    
    // Theme-aware gradient with glow integration
    val gradientColors = listOf(
        topHighlight,      // Theme surface highlight
        lightColor,        // Blended light color
        midColor,          // Blended mid tone
        glowTint,          // Theme-integrated glow
        accentColor        // Theme-primary blend
    )
    
    // Subtle, soft shadows for 3D depth effect (softer and less rectangular)
    val shadowElevation = when {
        isHovered && !isPressed -> 8.dp
        isPressed -> 4.dp
        else -> 6.dp
    }
    
    // Subtle glow intensity for soft glowing 3D effect
    val glowIntensity = when {
        isHovered && !isPressed -> 12.dp
        isPressed -> 6.dp
        else -> 8.dp
    }
    
    // Animated glow alpha for subtle pulsing effect
    val glowAlpha by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 0.35f
            isPressed -> 0.25f
            else -> 0.2f
        },
        animationSpec = tween(300)
    )
    
    // Use larger corner radius for softer, less rectangular appearance
    val cornerRadius = 24.dp
    
    // Outer Box for strong glow effect with multiple layers
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
            // Soft outer glow layer with theme colors
            .shadow(
                elevation = glowIntensity,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = colorScheme.primary.copy(alpha = glowAlpha * 0.4f),
                spotColor = glowColor.copy(alpha = glowAlpha * 0.5f)
            )
            // Soft middle glow layer for subtle depth
            .shadow(
                elevation = glowIntensity * 0.5f,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = colorScheme.primary.copy(alpha = glowAlpha * 0.25f),
                spotColor = glowColor.copy(alpha = glowAlpha * 0.3f)
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
                // Soft, subtle shadow using theme colors for depth
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = colorScheme.scrim.copy(alpha = if (isInteracting) 0.08f else 0.05f),
                    spotColor = colorScheme.scrim.copy(alpha = if (isInteracting) 0.12f else 0.08f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                // Theme-aware gradient background with glow
                .background(
                    brush = Brush.linearGradient(
                        start = Offset(0f, 0f),
                        end = Offset(0f, 1200f),
                        colors = gradientColors
                    )
                )
                // Subtle border using theme outline color
                .border(
                    width = if (isInteracting) 1.5.dp else 1.dp,
                    color = colorScheme.outlineVariant.copy(alpha = if (isInteracting) 0.4f else 0.25f),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            // Inner Box with subtle theme-based glow border
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 0.5.dp,
                        color = colorScheme.primary.copy(alpha = if (isInteracting) 0.25f else 0.15f),
                        shape = RoundedCornerShape(cornerRadius)
                    )
            )
            // Content with padding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Use Drawable icon if available, otherwise fall back to ImageVector
                    val drawablePainter = rememberDrawablePainter(appData.iconDrawable)
                    if (drawablePainter != null) {
                        Image(
                            painter = drawablePainter,
                            contentDescription = appData.name,
                            modifier = Modifier.size(22.dp)
                        )
                    } else if (appData.icon != null) {
                        Icon(
                            imageVector = appData.icon,
                            contentDescription = appData.name,
                            tint = appData.textColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = appData.name,
                        color = colorScheme.onSurface,
                        style = typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
