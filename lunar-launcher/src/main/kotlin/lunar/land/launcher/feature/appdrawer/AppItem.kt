package lunar.land.launcher.feature.appdrawer

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import lunar.land.ui.core.theme.LunarTheme

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
 * App button component matching ModeToggleButton style from TaskExecutorAgentScreen.
 * Features clean, simple design with subtle interactions.
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
    
    // Use press state for touch interactions
    val isInteracting = isPressed || isHovered
    
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
                onClick = onClick
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
            // Use Drawable icon if available, otherwise fall back to ImageVector
            val drawablePainter = rememberDrawablePainter(appData.iconDrawable)
            if (drawablePainter != null) {
                Image(
                    painter = drawablePainter,
                    contentDescription = appData.name,
                    modifier = Modifier.size(24.dp)
                )
            } else if (appData.icon != null) {
                Icon(
                    imageVector = appData.icon,
                    contentDescription = appData.name,
                    tint = LunarTheme.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
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
