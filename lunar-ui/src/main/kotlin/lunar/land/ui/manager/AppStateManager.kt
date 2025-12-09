package lunar.land.ui.manager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lunar.land.ui.manager.model.AppInfo

/**
 * Global app state manager that loads and caches apps at application level.
 * This should be initialized when the application starts.
 */
class AppStateManager(application: Application) : AndroidViewModel(application) {
    private val appManager = AppManager(application)
    private val appCache = AppCache(application)
    
    private val _appsState = MutableStateFlow<List<AppInfo>>(
        // Initialize with cached apps immediately
        run {
            val cachedMetadata = appCache.loadCachedAppsSync()
            if (cachedMetadata != null && cachedMetadata.isNotEmpty()) {
                cachedMetadata.map { metadata ->
                    AppInfo(
                        app = lunar.land.ui.core.model.app.App(
                            name = metadata.name,
                            displayName = metadata.displayName,
                            packageName = metadata.packageName,
                            isSystem = metadata.isSystem
                        ),
                        icon = null, // Icons loaded lazily in background
                        color = metadata.color
                    )
                }
            } else {
                emptyList()
            }
        }
    )
    
    val appsState: StateFlow<List<AppInfo>> = _appsState.asStateFlow()
    
    init {
        // Load apps in background and update cache
        loadAndCacheApps()
    }
    
    /**
     * Loads apps from system and updates cache.
     * This runs in the background and doesn't block UI.
     */
    private fun loadAndCacheApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appManager.getAllApps()
                    .catch {
                        // Silently handle errors
                        return@catch
                    }
                    .collect { freshApps ->
                        // Update state with fresh apps
                        _appsState.value = freshApps
                        
                        // Update cache
                        appCache.saveApps(freshApps)
                        
                        // Only collect once
                        return@collect
                    }
            } catch (e: Exception) {
                // Silently handle errors
            }
        }
    }
    
    /**
     * Checks for app changes and updates the list if needed.
     * Should be called after UI is shown.
     */
    fun checkForAppChanges() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentApps = _appsState.value
                val cachedAppCount = appCache.getCachedAppCount()
                val cachedPackageList = appCache.getCachedPackageList()
                
                // Quick check: get current app count first
                val resolveInfos = withContext(Dispatchers.IO) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    getApplication<android.app.Application>().packageManager.queryIntentActivities(intent, 0)
                }
                
                val currentAppCount = resolveInfos.size
                
                // If count is different, there are changes
                if (currentAppCount != cachedAppCount) {
                    // Get current package list
                    val currentPackageList = resolveInfos.mapNotNull { resolveInfo -> 
                        resolveInfo.activityInfo?.packageName 
                    }.toSet()
                    
                    // Find added and removed apps
                    val addedPackageNames = currentPackageList.filter { it !in cachedPackageList }.toSet()
                    val removedPackageNames = cachedPackageList.filter { it !in currentPackageList }.toSet()
                    
                    if (addedPackageNames.isNotEmpty() || removedPackageNames.isNotEmpty()) {
                        // Load full app list and update
                        val freshApps = appManager.getAllApps().first()
                        
                        val currentPackageNames = currentApps.map { it.app.packageName }.toSet()
                        val freshPackageNames = freshApps.map { it.app.packageName }.toSet()
                        
                        val added = freshPackageNames - currentPackageNames
                        val removed = currentPackageNames - freshPackageNames
                        
                        if (added.isNotEmpty() || removed.isNotEmpty()) {
                            val updatedApps = mutableListOf<AppInfo>()
                            
                            // Keep existing apps (excluding removed ones)
                            updatedApps.addAll(
                                currentApps.filter { it.app.packageName !in removed }
                            )
                            
                            // Add new apps
                            val newApps = freshApps.filter { it.app.packageName in added }
                            updatedApps.addAll(newApps)
                            
                            // Update icons for existing apps that don't have them
                            val freshAppsMap = freshApps.associateBy { it.app.packageName }
                            val appsWithIcons = updatedApps.map { currentApp ->
                                val freshApp = freshAppsMap[currentApp.app.packageName]
                                if (currentApp.icon == null && freshApp?.icon != null) {
                                    currentApp.copy(icon = freshApp.icon)
                                } else {
                                    currentApp
                                }
                            }
                            
                            // Sort by display name
                            val sortedApps = appsWithIcons.sortedBy { it.app.displayName.lowercase() }
                            
                            // Update state
                            _appsState.value = sortedApps
                            
                            // Save updated cache
                            appCache.saveApps(sortedApps)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently handle errors
            }
        }
    }
}

