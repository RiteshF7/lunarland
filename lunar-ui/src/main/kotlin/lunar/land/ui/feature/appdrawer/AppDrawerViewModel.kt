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
import lunar.land.ui.manager.model.AppInfo

/**
 * UI state for the app drawer.
 */
data class AppDrawerUiState(
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: Throwable? = null
)

/**
 * ViewModel for the app drawer screen.
 * Uses AppManager for fetching and processing apps.
 */
class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {
    private val appManager = AppManager(application)
    
    private val _uiState = MutableStateFlow(AppDrawerUiState())
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    /**
     * Loads all apps using the AppManager.
     * Processing happens in parallel on background threads.
     */
    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            appManager.getAllApps()
                .catch { exception ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = exception
                        )
                    }
                }
                .collect { apps ->
                    _uiState.update { 
                        it.copy(
                            allApps = apps,
                            filteredApps = filterApps(apps, it.searchQuery),
                            isLoading = false,
                            error = null
                        )
                    }
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

