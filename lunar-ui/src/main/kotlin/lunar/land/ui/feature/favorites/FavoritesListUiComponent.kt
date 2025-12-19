package lunar.land.ui.feature.favorites

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import lunar.land.ui.core.model.app.AppWithColor
import lunar.land.ui.core.ui.extensions.isOverlayPermissionGranted
import lunar.land.ui.core.ui.extensions.launchApp
import lunar.land.ui.core.ui.extensions.requestOverlayPermission
import lunar.land.ui.core.ui.extensions.startFloatingVolumeService
import lunar.land.ui.feature.appdrawer.AppItem
import lunar.land.ui.feature.appdrawer.AppItemData
import lunar.land.ui.feature.favorites.ui.StaggeredFlowRow

@Composable
fun FavoritesListUiComponent(
    state: FavoritesListUiComponentState,
    contentPadding: Dp,
    modifier: Modifier = Modifier
) {
    // Need to extract the eventSink out to a local val, so that the Compose Compiler
    // treats it as stable. See: https://issuetracker.google.com/issues/256100927
    val eventSink = state.eventSink

    FavoritesListUiComponent(
        modifier = modifier,
        favoritesList = state.favoritesList,
        addDefaultAppsToFavorites = { eventSink(FavoritesListUiComponentUiEvent.AddDefaultAppsIfRequired) },
        contentPadding = contentPadding
    )
}

@Composable
private fun FavoritesListUiComponent(
    favoritesList: ImmutableList<AppWithColor>,
    addDefaultAppsToFavorites: () -> Unit,
    contentPadding: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(key1 = favoritesList.isEmpty()) {
        if (favoritesList.isNotEmpty()) return@LaunchedEffect

        addDefaultAppsToFavorites()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        StaggeredFlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
            mainAxisSpacing = 14.dp,
            crossAxisSpacing = 14.dp
        ) {
            favoritesList.forEach { favoriteAppWithColor ->
                ReusableContent(key = favoriteAppWithColor.app) {
                    val appItemData = remember(favoriteAppWithColor) {
                        favoriteAppWithColor.toAppItemData(context)
                    }
                    AppItem(
                        appData = appItemData,
                        onClick = {
                            // Special handling for Driver Activity
                            if (favoriteAppWithColor.app.displayName == "Driver" && 
                                favoriteAppWithColor.app.packageName == context.packageName) {
                                // Launch DriverActivity directly
                                val intent = Intent(context, Class.forName("com.termux.app.DriverActivity"))
                                context.startActivity(intent)
                            } else if (favoriteAppWithColor.app.displayName == "Floating Volume" && 
                                favoriteAppWithColor.app.packageName == context.packageName) {
                                // Toggle Floating Volume Service
                                if (context.isOverlayPermissionGranted()) {
                                    context.startFloatingVolumeService()
                                } else {
                                    context.requestOverlayPermission()
                                }
                            } else {
                                // Launch other apps normally
                                context.launchApp(app = favoriteAppWithColor.app)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Converts AppWithColor to AppItemData for use with AppItem composable.
 * Generates dark-themed colors optimized for black background.
 * Similar to AppInfo.toAppItemData() but works with AppWithColor.
 */
private fun AppWithColor.toAppItemData(context: android.content.Context): AppItemData {
    val packageManager = context.packageManager
    
    // Get app icon drawable - try to get from launcher activity first, then fallback to application icon
    val iconDrawable = try {
        // Special handling for Driver Activity
        if (app.displayName == "Driver" && app.packageName == context.packageName) {
            // Try to get DriverActivity icon or use app icon
            try {
                val driverActivityInfo = packageManager.getActivityInfo(
                    android.content.ComponentName(context.packageName, "com.termux.app.DriverActivity"),
                    PackageManager.GET_META_DATA
                )
                driverActivityInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                // Fallback to application icon
                val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                packageManager.getApplicationIcon(appInfo)
            }
        } else if (app.displayName == "Floating Volume" && app.packageName == context.packageName) {
            // Use volume icon drawable for Floating Volume
            try {
                val resources = context.resources
                val drawableId = resources.getIdentifier("ic_volume", "drawable", context.packageName)
                if (drawableId != 0) {
                    resources.getDrawable(drawableId, null)
                } else {
                    // Fallback to application icon
                    val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                    packageManager.getApplicationIcon(appInfo)
                }
            } catch (e: Exception) {
                // Fallback to application icon
                val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                packageManager.getApplicationIcon(appInfo)
            }
        } else {
            // Try to get icon from launcher activity (preferred method)
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setPackage(app.packageName)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfos.firstOrNull()?.activityInfo?.let { activityInfo ->
                activityInfo.loadIcon(packageManager)
            } ?: run {
                // Fallback to application icon
                val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                packageManager.getApplicationIcon(appInfo)
            }
        }
    } catch (e: Exception) {
        null
    }
    
    // Get color from AppWithColor, or generate one if null
    val colorInt = color ?: lunar.land.ui.manager.model.AppInfo.generateColor(app.packageName)
    val color = Color(colorInt)
    
    // Create vibrant background that matches app's color theme
    // Use more of the app's actual color for better visual harmony
    val baseDark = 0.12f  // Base dark value for depth
    val colorContribution = 0.35f  // Strong color contribution for theme matching
    
    // Blend app color more prominently to match content
    val backgroundColor = Color(
        red = (color.red * colorContribution + baseDark).coerceIn(0f, 1f),
        green = (color.green * colorContribution + baseDark).coerceIn(0f, 1f),
        blue = (color.blue * colorContribution + baseDark).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    // Light text color with subtle color tint for readability on dark background
    val textColor = Color(
        red = (color.red * 0.3f + 0.85f).coerceIn(0f, 1f),
        green = (color.green * 0.3f + 0.85f).coerceIn(0f, 1f),
        blue = (color.blue * 0.3f + 0.85f).coerceIn(0f, 1f),
        alpha = 0.95f
    )
    
    // Glow color with more saturation for visibility on dark background
    val glowColor = Color(
        red = (color.red * 0.6f + 0.3f).coerceIn(0f, 1f),
        green = (color.green * 0.6f + 0.3f).coerceIn(0f, 1f),
        blue = (color.blue * 0.6f + 0.3f).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    return AppItemData(
        name = app.displayName,
        iconDrawable = iconDrawable,
        backgroundColor = backgroundColor,
        textColor = textColor,
        glowColor = glowColor,
        isWide = false
    )
}

