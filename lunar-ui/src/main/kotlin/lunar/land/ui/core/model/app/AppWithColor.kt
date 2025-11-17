package lunar.land.ui.core.model.app

import android.graphics.Color
import androidx.compose.runtime.Immutable

@Immutable
data class AppWithColor(
    val app: App,
    val color: Int? // Color as Int (ARGB format)
)

