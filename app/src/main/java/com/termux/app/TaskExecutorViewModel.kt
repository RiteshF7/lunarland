package com.termux.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.shared.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for Task Executor screen
 */
data class TaskExecutorUiState(
    val statusText: String = "",
    val outputText: String = "",
    val isUiEnabled: Boolean = false,
    val sessionFinished: Boolean = false,
    val exitCode: Int? = null
)

/**
 * ViewModel for Task Executor Activity
 * Manages UI state and provides methods for session operations
 */
class TaskExecutorViewModel(application: Application) : AndroidViewModel(application) {

    private val LOG_TAG = "TaskExecutorViewModel"

    private val _uiState = MutableStateFlow(TaskExecutorUiState())
    val uiState: StateFlow<TaskExecutorUiState> = _uiState.asStateFlow()

    /**
     * Update status text
     */
    fun updateStatus(status: String) {
        _uiState.update { it.copy(statusText = status) }
    }

    /**
     * Update output text
     */
    fun updateOutput(output: String) {
        _uiState.update { it.copy(outputText = output) }
    }

    /**
     * Set UI enabled state
     */
    fun setUiEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isUiEnabled = enabled) }
    }

    /**
     * Set session finished state
     */
    fun setSessionFinished(finished: Boolean, exitCode: Int? = null) {
        _uiState.update { 
            it.copy(
                sessionFinished = finished,
                exitCode = exitCode,
                isUiEnabled = !finished
            )
        }
    }

    /**
     * Clear output
     */
    fun clearOutput() {
        _uiState.update { it.copy(outputText = "") }
    }

    /**
     * Reset session state
     */
    fun resetSessionState() {
        _uiState.update { 
            it.copy(
                sessionFinished = false,
                exitCode = null,
                outputText = ""
            )
        }
    }
}

