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
    
    // Use app colors optimized for pure black background - brighter and more vibrant
    val baseColor = appData.backgroundColor
    val glowColor = appData.glowColor
    
    // Enhanced brightness and contrast for pure black background
    // Much brighter colors to stand out beautifully on black
    val topHighlightAlpha = if (isInteracting) 1f else 0.9f  // Maximum brightness
    val highlightAlpha = if (isInteracting) 0.85f else 0.75f
    val lightAlpha = if (isInteracting) 0.7f else 0.6f
    val midAlpha = if (isInteracting) 0.55f else 0.45f
    val accentAlpha = if (isInteracting) 0.4f else 0.3f
    val bottomAlpha = if (isInteracting) 0.25f else 0.2f
    val glowTintAlpha = if (isInteracting) 0.4f else 0.3f
    
    // Create vibrant gradient with strong contrast for pure black background
    // Much brighter colors that pop on black - using lighter greys with color tints
    val almostWhite = Color(0xFF6a6a6a)    // Brighter grey for top highlight
    val veryLightGrey = Color(0xFF5a5a5a)   // Brighter light grey
    val lightGrey = Color(0xFF4a4a4a)       // Brighter grey
    val midGrey = Color(0xFF3a3a3a)         // Medium-bright grey
    val darkGrey = Color(0xFF2a2a2a)       // Darker but still visible grey
    val almostBlack = Color(0xFF1a1a1a)    // Dark but not pure black for bottom
    
    // Vibrant gradient that matches app's color theme
    // Use more of the base color throughout for better harmony with content
    val topHighlight = Color(
        red = (baseColor.red * 0.7f + almostWhite.red * 0.3f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.7f + almostWhite.green * 0.3f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.7f + almostWhite.blue * 0.3f).coerceIn(0f, 1f),
        alpha = topHighlightAlpha
    )
    val highlight = Color(
        red = (baseColor.red * 0.75f + veryLightGrey.red * 0.25f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.75f + veryLightGrey.green * 0.25f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.75f + veryLightGrey.blue * 0.25f).coerceIn(0f, 1f),
        alpha = highlightAlpha
    )
    val lightColor = Color(
        red = (baseColor.red * 0.8f + lightGrey.red * 0.2f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.8f + lightGrey.green * 0.2f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.8f + lightGrey.blue * 0.2f).coerceIn(0f, 1f),
        alpha = lightAlpha
    )
    val midColor = Color(
        red = (baseColor.red * 0.85f + midGrey.red * 0.15f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.85f + midGrey.green * 0.15f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.85f + midGrey.blue * 0.15f).coerceIn(0f, 1f),
        alpha = midAlpha
    )
    val darkColor = Color(
        red = (baseColor.red * 0.75f + darkGrey.red * 0.25f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.75f + darkGrey.green * 0.25f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.75f + darkGrey.blue * 0.25f).coerceIn(0f, 1f),
        alpha = accentAlpha
    )
    val bottomColor = Color(
        red = (baseColor.red * 0.6f + almostBlack.red * 0.4f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.6f + almostBlack.green * 0.4f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.6f + almostBlack.blue * 0.4f).coerceIn(0f, 1f),
        alpha = bottomAlpha
    )
    
    // Enhanced glow tint - much more vibrant and visible on black
    val glowTint = Color(
        red = (glowColor.red * 0.9f + 0.15f).coerceIn(0f, 1f),
        green = (glowColor.green * 0.9f + 0.15f).coerceIn(0f, 1f),
        blue = (glowColor.blue * 0.9f + 0.15f).coerceIn(0f, 1f),
        alpha = glowTintAlpha
    )
    
    // Dramatic 3D gradient - very bright top, very dark bottom
    // Creates strong upward lift perception
    val gradientColors = listOf(
        topHighlight,      // Almost white top - maximum brightness
        highlight,         // Very light highlight
        lightColor,         // Light
        midColor,          // Medium
        darkColor,         // Dark
        glowTint,          // Glow for depth
        bottomColor        // Almost black bottom - maximum darkness
    )
    
    // Enhanced shadows for better depth on black background
    val shadowElevation = when {
        isHovered && !isPressed -> 12.dp
        isPressed -> 6.dp
        else -> 8.dp
    }
    
    // Stronger glow intensity for beautiful glowing effect on black
    val glowIntensity = when {
        isHovered && !isPressed -> 20.dp
        isPressed -> 12.dp
        else -> 16.dp
    }
    
    // Enhanced glow alpha - much more visible and beautiful on black
    val glowAlpha by animateFloatAsState(
        targetValue = when {
            isHovered && !isPressed -> 0.8f
            isPressed -> 0.6f
            else -> 0.65f
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
            // Strong outer glow layer - beautiful and vibrant on black
            .shadow(
                elevation = glowIntensity,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = glowColor.copy(alpha = glowAlpha * 0.6f),
                spotColor = glowColor.copy(alpha = glowAlpha * 0.8f)
            )
            // Enhanced middle glow layer for depth and beauty
            .shadow(
                elevation = glowIntensity * 0.7f,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = glowColor.copy(alpha = glowAlpha * 0.4f),
                spotColor = glowColor.copy(alpha = glowAlpha * 0.5f)
            )
            // Additional inner glow layer for extra depth
            .shadow(
                elevation = glowIntensity * 0.4f,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = glowColor.copy(alpha = glowAlpha * 0.3f),
                spotColor = glowColor.copy(alpha = glowAlpha * 0.35f)
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
                // Enhanced shadow for better depth on black background
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black.copy(alpha = if (isInteracting) 0.6f else 0.5f),
                    spotColor = Color.Black.copy(alpha = if (isInteracting) 0.7f else 0.6f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                // Dramatic upward 3D gradient - very bright top, very dark bottom
                .background(
                    brush = Brush.linearGradient(
                        start = Offset(0f, 0f),      // Top - brightest (almost white)
                        end = Offset(0f, 1200f),    // Bottom - darkest (almost black)
                        colors = gradientColors
                    )
                )
                // Enhanced top border highlight - brighter and more visible on black
                .border(
                    width = if (isInteracting) 2.dp else 1.5.dp,
                    color = Color(0xFF7a7a7a).copy(alpha = if (isInteracting) 0.9f else 0.75f),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            // Enhanced top highlight - brighter and more beautiful on black
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(
                        brush = Brush.linearGradient(
                            start = Offset(0f, 0f),
                            end = Offset(0f, 50f),  // Slightly larger highlight area
                            colors = listOf(
                                Color.White.copy(alpha = if (isInteracting) 0.25f else 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Inner Box with vibrant glow border - much more visible on black
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = if (isInteracting) 1.dp else 0.75.dp,
                        color = glowColor.copy(alpha = if (isInteracting) 0.5f else 0.4f),
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
                        color = appData.textColor, // Use app-specific text color for dark background
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
