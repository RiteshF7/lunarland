package lunar.land.ui.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
     */
    private suspend fun fetchResolveInfos(): List<ResolveInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
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
     * Returns null if the app cannot be processed.
     */
    private suspend fun processAppInfo(resolveInfo: ResolveInfo): AppInfo? = withContext(Dispatchers.IO) {
        try {
            val activityInfo = resolveInfo.activityInfo ?: return@withContext null

            // Load app basic info
            val app = createApp(activityInfo)
            
            // Load app icon (resource-intensive operation)
            val icon = loadAppIcon(activityInfo) ?: return@withContext null

            // Extract dominant color from icon, fallback to generated color
            val fallbackColor = AppInfo.generateColor(app.packageName)
            val color = ColorExtractor.extractDominantColor(icon, fallbackColor)

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
    private fun loadAppIcon(activityInfo: android.content.pm.ActivityInfo): android.graphics.drawable.Drawable? {
        return try {
            activityInfo.loadIcon(packageManager)
        } catch (e: Exception) {
            null
        }
    }
}

