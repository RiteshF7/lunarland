package lunar.land.ui.core.ui.remember

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import android.graphics.Color as GraphicsColor
import lunar.land.ui.core.ui.extensions.blendWith
import lunar.land.ui.core.ui.extensions.luminate

@Composable
fun rememberAppColor(graphicsColor: Int?): Color {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    return remember(graphicsColor, primaryColor, backgroundColor, contentColor) {
        var color = graphicsColor?.let(::Color) ?: primaryColor
        color = color.luminate(threshold = 0.36f, value = 0.6f)

        val contrastThreshold = 2.5f
        val contrast = ColorUtils.calculateContrast(color.toArgb(), backgroundColor.toArgb()).toFloat()

        if (contrast < contrastThreshold) color.blendWith(
            color = contentColor,
            ratio = (contrastThreshold - contrast) / contrastThreshold
        ) else color
    }
}
