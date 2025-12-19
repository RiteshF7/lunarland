package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import lunar.land.ui.core.theme.LunarTheme

/**
 * Converts a Drawable to a Painter for use in Compose
 * Optimized with proper caching and memory management.
 * Uses stable key based on drawable identity for efficient caching.
 */
@Composable
private fun rememberDrawablePainter(drawable: Drawable?): Painter? {
    // Use drawable's identity as key for stable caching
    // This prevents unnecessary bitmap recreations
    val drawableKey = remember(drawable) {
        drawable?.hashCode() ?: 0
    }
    
    return remember(drawableKey) {
        drawable?.let {
            // Use optimal bitmap size - limit to reasonable dimensions for icons
            val maxSize = 256 // Limit icon size for memory efficiency
            val width = it.intrinsicWidth.coerceIn(1, maxSize)
            val height = it.intrinsicHeight.coerceIn(1, maxSize)
            
            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, width, height)
            it.draw(canvas)
            BitmapPainter(bitmap.asImageBitmap())
        }
    }
}

/**
 * Isolated composable for app icon rendering.
 * This creates a composition boundary to prevent unnecessary recompositions.
 */
@Composable
private fun AppIcon(
    iconDrawable: Drawable?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    appName: String
) {
    // Use Drawable icon if available, otherwise fall back to ImageVector or placeholder
    val drawablePainter = rememberDrawablePainter(iconDrawable)
    
    when {
        drawablePainter != null -> {
            Image(
                painter = drawablePainter,
                contentDescription = appName,
                modifier = Modifier.size(24.dp)
            )
        }
        icon != null -> {
            Icon(
                imageVector = icon,
                contentDescription = appName,
                tint = LunarTheme.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        else -> {
            // Placeholder icon when icon is not yet loaded
            // Memoize first letter to avoid recalculation
            val firstLetter = remember(appName) {
                appName.take(1).uppercase()
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = LunarTheme.InactiveBackgroundColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstLetter,
                    style = LunarTheme.Typography.BodySmall,
                    color = LunarTheme.TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * App item component using app theme colors.
 * Features clean design matching the app's dark theme with green accents.
 * Optimized with composition boundaries and stable references.
 * 
 * @param appData The data for the app item
 * @param onClick Callback when the item is clicked
 * @param modifier Modifier for the composable
 */
@Composable
fun NeumorphicAppItem(
    appData: AppItemData,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Stable callback reference prevents unnecessary recompositions
    val stableOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    
    // Interaction source - stable reference per item
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Use press state for touch interactions
    // Memoize to avoid recalculation
    val isInteracting = remember(isPressed, isHovered) { isPressed || isHovered }
    
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
            .clip(RoundedCornerShape(LunarTheme.CornerRadius.Small))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        if (isInteracting) LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Medium) else LunarTheme.InactiveBackgroundColor,
                        if (isInteracting) LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Low) else LunarTheme.InactiveBackgroundColor
                    )
                )
            )
            .border(
                width = LunarTheme.BorderWidth,
                color = if (isInteracting) LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.High) else LunarTheme.BorderColor,
                shape = RoundedCornerShape(LunarTheme.CornerRadius.Small)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { stableOnClick() }
            )
    ) {
        // Content with padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Isolate icon rendering in its own composition boundary
            // This prevents recomposition of the entire item when only icon changes
            AppIcon(
                iconDrawable = appData.iconDrawable,
                icon = appData.icon,
                appName = appData.name
            )
            Spacer(modifier = Modifier.width(LunarTheme.Spacing.Medium))
            Text(
                text = appData.name,
                color = LunarTheme.TextPrimary,
                style = LunarTheme.Typography.BodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

