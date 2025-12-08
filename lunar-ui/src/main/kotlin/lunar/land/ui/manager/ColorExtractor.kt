package lunar.land.ui.manager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for extracting dominant colors from app icons.
 */
object ColorExtractor {

    /**
     * Extracts the dominant color from a Drawable icon.
     * Falls back to a generated color if extraction fails.
     * 
     * @param drawable The app icon drawable
     * @param fallbackColor A fallback color to use if extraction fails
     * @return The extracted dominant color, or fallback color
     */
    suspend fun extractDominantColor(
        drawable: Drawable,
        fallbackColor: Int
    ): Int = withContext(Dispatchers.Default) {
        try {
            val bitmap = drawableToBitmap(drawable)
            val palette = Palette.from(bitmap).generate()
            
            // Try to get vibrant color first, then muted, then dominant
            palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: fallbackColor
        } catch (e: Exception) {
            fallbackColor
        }
    }

    /**
     * Converts a Drawable to a Bitmap for color extraction.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        
        return bitmap
    }
}

