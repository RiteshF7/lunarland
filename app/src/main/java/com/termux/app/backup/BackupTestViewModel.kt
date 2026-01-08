package com.termux.app.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupTestUiState(
    val status: String = "Ready",
    val isInProgress: Boolean = false,
    val logs: List<String> = emptyList(),
    val isSuccess: Boolean = false,
    val error: String? = null
)

class BackupTestViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BackupTestUiState())
    val uiState: StateFlow<BackupTestUiState> = _uiState.asStateFlow()
    
    fun startTest() {
        _uiState.update {
            it.copy(
                status = "Starting...",
                isInProgress = true,
                logs = emptyList(),
                isSuccess = false,
                error = null
            )
        }
    }
    
    fun appendLog(message: String) {
        _uiState.update { state ->
            state.copy(
                logs = state.logs + message
            )
        }
    }
    
    fun onSuccess() {
        _uiState.update {
            it.copy(
                status = "Success",
                isInProgress = false,
                isSuccess = true
            )
        }
    }
    
    fun onError(error: String) {
        _uiState.update {
            it.copy(
                status = "Failed",
                isInProgress = false,
                error = error
            )
        }
        appendLog("Error: $error")
    }
    
    fun cancel() {
        _uiState.update {
            it.copy(
                status = "Cancelled",
                isInProgress = false
            )
        }
    }
    
    fun reset() {
        _uiState.update { BackupTestUiState() }
    }
}

