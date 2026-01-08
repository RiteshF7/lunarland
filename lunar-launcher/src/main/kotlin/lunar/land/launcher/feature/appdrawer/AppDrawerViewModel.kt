package lunar.land.launcher.feature.appdrawer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lunar.land.ui.manager.AppStateManager
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
 * Isolated component that takes app data from AppStateManager.
 * Only handles UI state and filtering.
 */
class AppDrawerViewModel(
    application: Application,
    private val appStateManager: AppStateManager
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(
        AppDrawerUiState(
            allApps = appStateManager.appsState.value,
            filteredApps = filterApps(appStateManager.appsState.value, ""),
            // Only show loading if cache is empty (first launch)
            isLoading = appStateManager.appsState.value.isEmpty()
        )
    )
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

    init {
        // Observe app state changes from AppStateManager
        viewModelScope.launch {
            appStateManager.appsState.collect { apps ->
                _uiState.update {
                    it.copy(
                        allApps = apps,
                        filteredApps = filterApps(apps, it.searchQuery),
                        // Never show loading if we have apps (even without icons)
                        isLoading = false
                    )
                }
            }
        }
        
        // Check for changes in background (non-blocking)
        viewModelScope.launch(Dispatchers.IO) {
            // Small delay to let UI render first
            kotlinx.coroutines.delay(500)
            appStateManager.checkForAppChanges()
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
     * Optimized for performance.
     */
    private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) {
            return apps
        }
        
        // Pre-compute lowercase query once
        val lowerQuery = query.lowercase()
        return apps.filter { appInfo ->
            appInfo.app.displayName.lowercase().contains(lowerQuery) ||
            appInfo.app.packageName.lowercase().contains(lowerQuery)
        }
    }
    
}

