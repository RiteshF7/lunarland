package com.termux.app.driver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.shared.logger.Logger
import com.termux.shared.termux.file.TermuxFileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for DriverActivity.
 */
data class DriverUiState(
    val isBootstrapReady: Boolean = false,
    val statusText: String = "Checking bootstrap status..."
)

/**
 * ViewModel to manage DriverActivity state.
 * Simplified to only track bootstrap status.
 */
class DriverViewModel : ViewModel() {
    private val LOG_TAG = "DriverViewModel"

    private val _uiState = MutableStateFlow(DriverUiState())
    val uiState: StateFlow<DriverUiState> = _uiState.asStateFlow()

    /**
     * Checks if bootstrap is installed and updates UI state.
     */
    fun checkBootstrapStatus(context: Context) {
        viewModelScope.launch {
            try {
                val isAccessible = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false) == null
                val isEmpty = TermuxFileUtils.isTermuxPrefixDirectoryEmpty()
                val isReady = isAccessible && !isEmpty

                _uiState.update { currentState ->
                    currentState.copy(
                        isBootstrapReady = isReady,
                        statusText = if (isReady) {
                            "Bootstrap ready"
                        } else {
                            "Bootstrap not installed. Use Bootstrap Setup button to install."
                        }
                    )
                }

                Logger.logInfo(LOG_TAG, "Bootstrap status checked: ready=$isReady")
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Error checking bootstrap status: ${e.message}")
                _uiState.update { currentState ->
                    currentState.copy(
                        statusText = "Error checking bootstrap status"
                    )
                }
            }
        }
    }
}

