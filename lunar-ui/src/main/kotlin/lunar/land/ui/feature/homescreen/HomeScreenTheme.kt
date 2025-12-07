package lunar.land.ui.feature.homescreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import lunar.land.ui.R

/**
 * Theme constants for HomeScreen matching TaskExecutorAgentScreen design.
 */
object HomeScreenTheme {
    /**
     * Background color matching the HTML design.
     */
    val backgroundColor = Color(0xFF0a0f0a) // #0a0f0a
    
    /**
     * Gradient start color for radial gradient overlay.
     */
    val gradientStartColor = Color(0xFF0e1a10) // #0e1a10
    
    /**
     * Accent color used for highlights and UI elements.
     */
    val accentColor = Color(0xFF4DFF88) // #4DFF88
    
    /**
     * Manrope font family matching the HTML design.
     */
    val manropeFontFamily = FontFamily(
        Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
    )
}

