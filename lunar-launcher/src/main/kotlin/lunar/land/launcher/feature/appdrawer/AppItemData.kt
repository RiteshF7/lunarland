package lunar.land.launcher.feature.appdrawer

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data class representing an app item in the drawer.
 * 
 * @param name The display name of the app
 * @param icon The icon to display for the app (ImageVector for default icons)
 * @param iconDrawable The drawable icon to display (takes precedence over icon if provided)
 * @param backgroundColor The background color of the app button
 * @param textColor The text and icon color
 * @param glowColor The glow effect color
 * @param isWide Whether this item should span wider (e.g., 75% width)
 */
data class AppItemData(
    val name: String,
    val icon: ImageVector? = null,
    val iconDrawable: Drawable? = null,
    val backgroundColor: Color,
    val textColor: Color,
    val glowColor: Color,
    val isWide: Boolean = false
)
