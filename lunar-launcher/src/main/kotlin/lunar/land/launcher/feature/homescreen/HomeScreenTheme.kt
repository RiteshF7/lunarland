package lunar.land.launcher.feature.homescreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import lunar.land.ui.R

/**
 * Theme constants for HomeScreen using MaterialTheme colors with darker adjustments.
 */
object HomeScreenTheme {
    /**
     * Manrope font family matching the HTML design.
     */
    val manropeFontFamily = FontFamily(
        Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
    )
    
    /**
     * Get darker background color based on theme.
     * Creates a darker version by blending theme background with black.
     */
    @Composable
    fun backgroundColor(): Color {
        val colorScheme = MaterialTheme.colorScheme
        return Color(
            red = (colorScheme.background.red * 0.3f + 0.05f).coerceIn(0f, 1f),
            green = (colorScheme.background.green * 0.3f + 0.05f).coerceIn(0f, 1f),
            blue = (colorScheme.background.blue * 0.3f + 0.05f).coerceIn(0f, 1f)
        )
    }
    
    /**
     * Get darker gradient start color based on theme.
     */
    @Composable
    fun gradientStartColor(): Color {
        val colorScheme = MaterialTheme.colorScheme
        return Color(
            red = (colorScheme.surface.red * 0.4f + 0.08f).coerceIn(0f, 1f),
            green = (colorScheme.surface.green * 0.4f + 0.08f).coerceIn(0f, 1f),
            blue = (colorScheme.surface.blue * 0.4f + 0.08f).coerceIn(0f, 1f)
        )
    }
    
    /**
     * Get accent color based on theme primary color.
     */
    @Composable
    fun accentColor(): Color {
        val colorScheme = MaterialTheme.colorScheme
        return Color(
            red = (colorScheme.primary.red * 0.6f + 0.2f).coerceIn(0f, 1f),
            green = (colorScheme.primary.green * 0.6f + 0.4f).coerceIn(0f, 1f),
            blue = (colorScheme.primary.blue * 0.6f + 0.3f).coerceIn(0f, 1f)
        )
    }
}

