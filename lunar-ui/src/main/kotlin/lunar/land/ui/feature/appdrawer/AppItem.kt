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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import lunar.land.ui.R

/**
 * Manrope font family matching TaskExecutorAgentScreen theme.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

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
    
    // Theme colors from TaskExecutorAgentScreen
    val accentColor = Color(0xFF4DFF88)
    val inputBackgroundColor = Color(0xFF1a1f1a)
    val borderColor = Color(0xFF2a3a2a)
    
    // Use theme background colors with subtle app color tint
    val baseColor = appData.backgroundColor
    val baseBackground = Color(
        red = (baseColor.red * 0.2f + inputBackgroundColor.red * 0.8f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.2f + inputBackgroundColor.green * 0.8f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.2f + inputBackgroundColor.blue * 0.8f).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    // Simple gradient matching TaskExecutorAgentScreen theme
    val gradientColors = listOf(
        baseBackground.copy(alpha = if (isInteracting) 0.9f else 0.8f),
        baseBackground.copy(alpha = if (isInteracting) 0.7f else 0.6f)
    )
    
    // Theme matching corner radius
    val cornerRadius = 16.dp
    
    // Outer Box matching TaskExecutorAgentScreen theme
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
                .clip(RoundedCornerShape(cornerRadius))
                // Theme matching gradient background
                .background(
                    brush = Brush.verticalGradient(
                        colors = gradientColors
                    )
                )
                // Thin border matching TaskExecutorAgentScreen theme
                .border(
                    width = 1.dp,
                    color = if (isInteracting) accentColor.copy(alpha = 0.3f) else borderColor,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
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
                            tint = Color.White.copy(alpha = 0.9f), // White icon matching theme
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = appData.name,
                        color = Color.White.copy(alpha = 0.9f), // White text matching theme
                        style = typography.bodyMedium.copy(
                            fontFamily = manropeFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
