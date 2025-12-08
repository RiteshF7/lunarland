package lunar.land.ui.feature.appdrawer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lunar.land.ui.manager.AppManager
import lunar.land.ui.manager.AppCache
import lunar.land.ui.manager.model.AppInfo

/**
 * UI state for the app drawer.
 */
data class AppDrawerUiState(
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false, // Indicates incremental update in progress
    val error: Throwable? = null
)

/**
 * ViewModel for the app drawer screen.
 * Uses AppManager for fetching and processing apps.
 * Implements caching for fast loading and incremental updates.
 */
class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {
    private val appManager = AppManager(application)
    private val appCache = AppCache(application)
    
    private val _uiState = MutableStateFlow(AppDrawerUiState())
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

    init {
        // Load cache immediately for instant display (synchronous on IO thread)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cachedMetadata = appCache.loadCachedApps()
            if (cachedMetadata != null && cachedMetadata.isNotEmpty()) {
                val cachedApps = cachedMetadata.map { metadata ->
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
                _uiState.update { 
                    it.copy(
                        allApps = cachedApps,
                        filteredApps = filterApps(cachedApps, it.searchQuery),
                        isLoading = false
                    )
                }
            } else {
                // No cache, set loading state
                _uiState.update { it.copy(isLoading = true) }
            }
        }
        // Then load fresh apps in background to update cache and load icons
        loadApps()
    }

    /**
     * Loads apps with caching strategy:
     * 1. Cache is already loaded in init (fast, synchronous)
     * 2. Check for app changes in background
     * 3. Incrementally update the list if apps were added/removed
     * 4. Load icons lazily in background
     */
    fun loadApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val currentApps = _uiState.value.allApps
            val hasCache = currentApps.isNotEmpty()
            
            // Load fresh apps in background and check for changes
            appManager.getAllApps()
                .catch { exception ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            isUpdating = false,
                            error = exception
                        )
                    }
                }
                .collect { freshApps ->
                    if (!hasCache && currentApps.isEmpty()) {
                        // First load - no cache, just set the fresh apps
                        _uiState.update { 
                            it.copy(
                                allApps = freshApps,
                                filteredApps = filterApps(freshApps, it.searchQuery),
                                isLoading = false,
                                isUpdating = false
                            )
                        }
                        appCache.saveApps(freshApps)
                    } else {
                        // Calculate diff - find added and removed apps
                        val currentPackageNames = currentApps.map { it.app.packageName }.toSet()
                        val freshPackageNames = freshApps.map { it.app.packageName }.toSet()
                        
                        val addedPackageNames = freshPackageNames - currentPackageNames
                        val removedPackageNames = currentPackageNames - freshPackageNames
                        
                        if (addedPackageNames.isEmpty() && removedPackageNames.isEmpty()) {
                            // No changes, just update icons lazily and save cache
                            val updatedApps = updateAppIcons(currentApps, freshApps)
                            _uiState.update { 
                                it.copy(
                                    allApps = updatedApps,
                                    filteredApps = filterApps(updatedApps, it.searchQuery),
                                    isLoading = false,
                                    isUpdating = false
                                )
                            }
                            appCache.saveApps(updatedApps)
                        } else {
                            // Incremental update: add new apps and remove deleted ones
                            _uiState.update { it.copy(isUpdating = true) }
                            
                            val updatedApps = mutableListOf<AppInfo>()
                            
                            // Keep existing apps (excluding removed ones)
                            updatedApps.addAll(
                                currentApps.filter { it.app.packageName !in removedPackageNames }
                            )
                            
                            // Add new apps
                            val newApps = freshApps.filter { it.app.packageName in addedPackageNames }
                            updatedApps.addAll(newApps)
                            
                            // Update icons for existing apps that don't have them
                            val appsWithIcons = updateAppIcons(updatedApps, freshApps)
                            
                            // Sort by display name
                            val sortedApps = appsWithIcons.sortedBy { it.app.displayName.lowercase() }
                            
                            _uiState.update { 
                                it.copy(
                                    allApps = sortedApps,
                                    filteredApps = filterApps(sortedApps, it.searchQuery),
                                    isLoading = false,
                                    isUpdating = false
                                )
                            }
                            
                            // Save updated cache
                            appCache.saveApps(sortedApps)
                        }
                    }
                }
        }
    }
    
    /**
     * Updates icons for apps that don't have them yet.
     * Matches apps by package name.
     */
    private fun updateAppIcons(
        currentApps: List<AppInfo>,
        freshApps: List<AppInfo>
    ): List<AppInfo> {
        val freshAppsMap = freshApps.associateBy { it.app.packageName }
        
        return currentApps.map { currentApp ->
            val freshApp = freshAppsMap[currentApp.app.packageName]
            if (currentApp.icon == null && freshApp?.icon != null) {
                currentApp.copy(icon = freshApp.icon)
            } else {
                currentApp
            }
        }
    }

    /**
     * Updates the search query and filters the apps.
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredApps = filterApps(state.allApps, query)
            )
        }
    }

    /**
     * Filters apps based on the search query.
     */
    private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) {
            return apps
        }
        
        val lowerQuery = query.lowercase()
        return apps.filter { appInfo ->
            appInfo.app.displayName.lowercase().contains(lowerQuery) ||
            appInfo.app.packageName.lowercase().contains(lowerQuery)
        }
    }
}

