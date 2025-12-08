package lunar.land.ui.core.ui.effects

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import lunar.land.ui.core.model.PackageAction

@Composable
fun PackageActionListener(
    onAction: (PackageAction) -> Unit
) {
    val context = LocalContext.current
    val updatedOnAction by rememberUpdatedState(newValue = onAction)

    DisposableEffect(key1 = context, key2 = updatedOnAction) {
        @Suppress("NewApi") // LauncherApps requires API 21+, getSystemService(Class) requires API 23+, but we check at runtime
        val launcherApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(LauncherApps::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        } ?: return@DisposableEffect onDispose {}
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                packageName ?: return
                updatedOnAction(PackageAction.Removed(packageName = packageName))
            }

            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                packageName ?: return
                updatedOnAction(PackageAction.Added(packageName = packageName))
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                packageName ?: return
                updatedOnAction(PackageAction.Updated(packageName = packageName))
            }

            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = Unit
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = Unit
        }

        launcherApps.registerCallback(callback)

        onDispose { launcherApps.unregisterCallback(callback) }
    }
}

