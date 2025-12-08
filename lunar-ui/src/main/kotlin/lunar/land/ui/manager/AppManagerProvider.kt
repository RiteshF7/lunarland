package lunar.land.ui.manager

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Provides an instance of AppManager using the current context.
 * This ensures the manager is created with the correct context.
 */
@Composable
fun rememberAppManager(): AppManager {
    val context = LocalContext.current
    return androidx.compose.runtime.remember { AppManager(context) }
}

