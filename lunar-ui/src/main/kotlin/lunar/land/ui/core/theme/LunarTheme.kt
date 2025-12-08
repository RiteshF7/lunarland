package lunar.land.ui.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R

/**
 * Centralized theme for Lunar Land app.
 * This is the single source of truth for all colors, typography, and spacing
 * used throughout the application, ensuring visual consistency.
 * 
 * Based on the TaskExecutorAgentScreen design with:
 * - Black background (#000000)
 * - Green accent color (#4DFF88)
 * - Manrope font family
 * - Modern, minimal aesthetic
 */
object LunarTheme {
    // ==================== Colors ====================
    
    /**
     * Primary accent color - vibrant green used for active states, highlights, and accents.
     * Hex: #4DFF88
     */
    val AccentColor = Color(0xFF4DFF88)
    
    /**
     * Main background color - pure black.
     * Hex: #000000
     */
    val BackgroundColor = Color.Black
    
    /**
     * Secondary background color - very dark green-tinted black.
     * Used for panels, cards, and elevated surfaces.
     * Hex: #0a0f0a
     */
    val SecondaryBackgroundColor = Color(0xFF0a0f0a)
    
    /**
     * Inactive/disabled background color - dark green-tinted gray.
     * Used for inactive buttons, input fields, and disabled states.
     * Hex: #1a1f1a
     */
    val InactiveBackgroundColor = Color(0xFF1a1f1a)
    
    /**
     * Border color - dark green-tinted gray.
     * Used for borders around cards, buttons, and input fields.
     * Hex: #2a3a2a
     */
    val BorderColor = Color(0xFF2a3a2a)
    
    /**
     * Primary text color - white.
     * Used for main text content.
     */
    val TextPrimary = Color.White
    
    /**
     * Secondary text color - white with reduced opacity.
     * Used for secondary text, placeholders, and inactive text.
     */
    val TextSecondary = Color.White.copy(alpha = 0.6f)
    
    /**
     * Tertiary text color - white with very low opacity.
     * Used for hints, placeholders, and subtle text.
     */
    val TextTertiary = Color.White.copy(alpha = 0.4f)
    
    /**
     * Disabled text color - white with very low opacity.
     * Used for disabled states.
     */
    val TextDisabled = Color.White.copy(alpha = 0.4f)
    
    // ==================== Typography ====================
    
    /**
     * Manrope font family - the primary font used throughout the app.
     * Matches the HTML design specification.
     */
    val ManropeFontFamily = FontFamily(
        Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
    )
    
    /**
     * Typography scale using Manrope font family.
     * All text styles should use these predefined sizes and weights.
     */
    object Typography {
        /**
         * Large display text - 32sp, SemiBold
         * Used for page titles and major headings.
         */
        val DisplayLarge = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        /**
         * Medium display text - 24sp, SemiBold
         * Used for section headings.
         */
        val DisplayMedium = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        /**
         * Small display text - 20sp, SemiBold
         * Used for subsection headings.
         */
        val DisplaySmall = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        /**
         * Large body text - 16sp, Medium
         * Used for important body text.
         */
        val BodyLarge = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        
        /**
         * Medium body text - 14sp, Medium
         * Used for standard body text and button labels.
         */
        val BodyMedium = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        
        /**
         * Small body text - 12sp, Medium
         * Used for secondary information and captions.
         */
        val BodySmall = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        
        /**
         * Button text - 15sp, SemiBold
         * Used for button labels.
         */
        val Button = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentColor
        )
        
        /**
         * Input text - 14sp, Medium
         * Used for text input fields.
         */
        val Input = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        
        /**
         * Placeholder text - 14sp, Medium
         * Used for input placeholders.
         */
        val Placeholder = androidx.compose.ui.text.TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextTertiary
        )
    }
    
    // ==================== Spacing ====================
    
    /**
     * Spacing scale for consistent padding and margins.
     */
    object Spacing {
        val ExtraSmall = 4.dp
        val Small = 8.dp
        val Medium = 12.dp
        val Large = 16.dp
        val ExtraLarge = 20.dp
        val XXLarge = 24.dp
        val XXXLarge = 32.dp
    }
    
    // ==================== Corner Radius ====================
    
    /**
     * Corner radius scale for consistent rounded corners.
     */
    object CornerRadius {
        /**
         * Small radius - 8dp
         * Used for small buttons and badges.
         */
        val Small = 8.dp
        
        /**
         * Medium radius - 12dp
         * Used for standard buttons, cards, and input fields.
         */
        val Medium = 12.dp
        
        /**
         * Large radius - 16dp
         * Used for large panels and containers.
         */
        val Large = 16.dp
    }
    
    // ==================== Border Width ====================
    
    /**
     * Standard border width - 1dp
     * Used for all borders throughout the app.
     */
    val BorderWidth = 1.dp
    
    // ==================== Alpha Values ====================
    
    /**
     * Alpha values for consistent transparency effects.
     */
    object Alpha {
        /**
         * High opacity - 0.3
         * Used for active borders and strong highlights.
         */
        val High = 0.3f
        
        /**
         * Medium opacity - 0.15
         * Used for active backgrounds and moderate highlights.
         */
        val Medium = 0.15f
        
        /**
         * Low opacity - 0.08
         * Used for subtle backgrounds and gentle highlights.
         */
        val Low = 0.08f
        
        /**
         * Very low opacity - 0.05
         * Used for very subtle effects.
         */
        val VeryLow = 0.05f
    }
}

