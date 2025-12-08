package lunar.land.ui.feature.appdrawer

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
    
    // Theme colors matching ModeToggleButton
    val accentColor = Color(0xFF4DFF88)
    val inactiveBackgroundColor = Color(0xFF1a1f1a)
    val borderColor = Color(0xFF2a3a2a)
    
    // Theme matching corner radius - same as ModeToggleButton
    val cornerRadius = 12.dp
    val typography = MaterialTheme.typography
    
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
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        if (isInteracting) accentColor.copy(alpha = 0.15f) else inactiveBackgroundColor,
                        if (isInteracting) accentColor.copy(alpha = 0.08f) else inactiveBackgroundColor
                    )
                )
            )
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
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = appData.name,
                color = Color.White,
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
