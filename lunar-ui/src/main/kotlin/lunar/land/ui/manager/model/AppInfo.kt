package lunar.land.ui.manager.model

import android.graphics.Color as GraphicsColor
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import lunar.land.ui.core.model.app.App

/**
 * Complete app information including icon and color.
 */
@Immutable
data class AppInfo(
    val app: App,
    val icon: Drawable,
    val color: Int
) {
    companion object {
        /**
         * Generates a consistent color for an app based on its package name.
         */
        fun generateColor(packageName: String): Int {
            val random = kotlin.random.Random(packageName.hashCode())
            return GraphicsColor.rgb(
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
            )
        }
    }
}

