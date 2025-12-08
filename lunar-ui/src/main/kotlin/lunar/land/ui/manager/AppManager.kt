package lunar.land.ui.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import lunar.land.ui.core.model.app.App
import lunar.land.ui.manager.model.AppInfo
import android.graphics.Color as GraphicsColor

/**
 * Manager responsible for fetching and processing app information.
 * Handles resource-intensive operations like loading app icons in parallel.
 */
class AppManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Fetches all installed apps as a Flow, processing them in parallel.
     * This is resource-intensive and should be called on a background thread.
     */
    fun getAllApps(): Flow<List<AppInfo>> = flow {
        val resolveInfos = fetchResolveInfos()
        val appInfos = processAppsInParallel(resolveInfos)
        emit(appInfos.sortedBy { it.app.displayName.lowercase() })
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches all resolve infos for launcher apps.
     * Uses flag 0 to get all apps, not just default ones.
     * This is important for launcher apps to see all installed apps.
     * With QUERY_ALL_PACKAGES permission, this will discover all apps including
     * those that might be filtered by package visibility restrictions on Android 11+.
     */
    private suspend fun fetchResolveInfos(): List<ResolveInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        // Use flag 0 to get all apps, not just default ones
        // MATCH_DEFAULT_ONLY would filter out some apps
        // With QUERY_ALL_PACKAGES permission in manifest, this will work on all Android versions
        packageManager.queryIntentActivities(intent, 0)
    }

    /**
     * Processes apps in parallel for better performance.
     * Each app's icon loading and processing happens concurrently.
     */
    private suspend fun processAppsInParallel(
        resolveInfos: List<ResolveInfo>
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        resolveInfos
            .map { resolveInfo ->
                async {
                    processAppInfo(resolveInfo)
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * Processes a single app's information.
     * Returns null only if the app cannot be processed at all (e.g., no activity info).
     * Apps with missing icons will still be included with a fallback icon.
     */
    private suspend fun processAppInfo(resolveInfo: ResolveInfo): AppInfo? = withContext(Dispatchers.IO) {
        try {
            val activityInfo = resolveInfo.activityInfo ?: return@withContext null

            // Load app basic info
            val app = createApp(activityInfo)
            
            // Load app icon (resource-intensive operation)
            // Try multiple methods to get the icon
            val icon = loadAppIcon(activityInfo) 
                ?: loadAppIconFromPackage(activityInfo.packageName)
                ?: createFallbackIcon(app.displayName)

            // Extract dominant color from icon, fallback to generated color
            val fallbackColor = AppInfo.generateColor(app.packageName)
            val color = if (icon != null) {
                ColorExtractor.extractDominantColor(icon, fallbackColor)
            } else {
                fallbackColor
            }

            AppInfo(app = app, icon = icon, color = color)
        } catch (e: Exception) {
            // Log error if needed, but don't crash
            null
        }
    }

    /**
     * Creates an App model from ActivityInfo.
     */
    private fun createApp(activityInfo: android.content.pm.ActivityInfo): App {
        val label = activityInfo.loadLabel(packageManager).toString()
        val isSystem = (activityInfo.applicationInfo.flags 
            and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

        return App(
            name = label,
            displayName = label,
            packageName = activityInfo.packageName,
            isSystem = isSystem
        )
    }

    /**
     * Loads the app icon from ActivityInfo.
     * This is a resource-intensive operation.
     */
    private fun loadAppIcon(activityInfo: android.content.pm.ActivityInfo): Drawable? {
        return try {
            activityInfo.loadIcon(packageManager)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Attempts to load app icon from package name as a fallback.
     */
    private fun loadAppIconFromPackage(packageName: String): Drawable? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Creates a simple fallback icon when app icon cannot be loaded.
     * This ensures apps are still shown even if icon loading fails.
     */
    private fun createFallbackIcon(appName: String): Drawable? {
        return try {
            // Use the default Android icon as fallback
            packageManager.getDefaultActivityIcon()
        } catch (e: Exception) {
            null
        }
    }
}

