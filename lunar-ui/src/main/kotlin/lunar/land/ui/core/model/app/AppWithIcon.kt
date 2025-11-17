package lunar.land.ui.core.model.app

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
data class AppWithIcon(
    val app: App,
    val icon: Drawable
) {
    val uniqueKey: String
        get() = app.packageName
}

