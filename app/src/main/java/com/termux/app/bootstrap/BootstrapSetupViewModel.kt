package com.termux.app.bootstrap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UI state for bootstrap setup screen.
 */
data class BootstrapSetupUiState(
    val logs: List<String> = emptyList(),
    val progress: Int = 0,
    val error: String? = null,
    val status: BootstrapStatus = BootstrapStatus.Idle,
    val isSetupInProgress: Boolean = false,
    val detectedArch: String? = null,
    val localBootstrapFile: File? = null,
    val bootstrapAlreadyInstalled: Boolean = false
)

/**
 * Status of bootstrap setup process.
 */
enum class BootstrapStatus {
    Idle,
    Detecting,
    Downloading,
    Installing,
    Completed,
    Failed,
    Cancelled
}

/**
 * ViewModel for bootstrap setup with automatic detection and installation.
 */
class BootstrapSetupViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(BootstrapSetupUiState())
    val uiState: StateFlow<BootstrapSetupUiState> = _uiState.asStateFlow()
    
    private val LOG_TAG = "BootstrapSetupViewModel"
    
    private var setupJob: Job? = null
    private var isCancelled = false
    
    // Throttling for progress updates
    private var lastDownloadProgressPercent = 0
    private var lastDownloadLogUpdateTimeMs = 0L
    
    init {
        // Automatically start bootstrap setup when ViewModel is created
        startAutomaticSetup()
    }
    
    /**
     * Automatically detects and starts bootstrap setup process.
     */
    fun startAutomaticSetup() {
        if (_uiState.value.isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress")
            return
        }
        
        isCancelled = false
        appendLog("Starting automatic bootstrap setup...")
        
        setupJob = viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isSetupInProgress = true,
                        status = BootstrapStatus.Detecting,
                        error = null,
                        progress = 0
                    )
                }
                
                // Step 1: Detect bootstrap status and architecture
                appendLog("Detecting bootstrap status and architecture...")
                val detectionResult = withContext(Dispatchers.IO) {
                    BootstrapManager.detectBootstrap(getApplication())
                }
                
                if (isCancelled) {
                    appendLog("Setup cancelled by user")
                    _uiState.update { 
                        it.copy(
                            status = BootstrapStatus.Cancelled,
                            isSetupInProgress = false
                        )
                    }
                    return@launch
                }
                
                _uiState.update {
                    it.copy(
                        detectedArch = detectionResult.architecture,
                        localBootstrapFile = detectionResult.localBootstrapFile,
                        bootstrapAlreadyInstalled = detectionResult.isInstalled,
                        progress = 10
                    )
                }
                
                if (detectionResult.isInstalled) {
                    appendLog("Bootstrap is already installed.")
                    _uiState.update {
                        it.copy(
                            status = BootstrapStatus.Completed,
                            progress = 100,
                            isSetupInProgress = false
                        )
                    }
                    return@launch
                }
                
                val arch = detectionResult.architecture
                if (arch == null) {
                    throw Exception("Failed to detect device architecture")
                }
                
                appendLog("Detected architecture: $arch")
                
                // Step 2: Check if download is needed
                val needsDownload = withContext(Dispatchers.IO) {
                    BootstrapManager.needsDownload(getApplication(), arch)
                }
                
                if (isCancelled) {
                    appendLog("Setup cancelled by user")
                    _uiState.update { 
                        it.copy(
                            status = BootstrapStatus.Cancelled,
                            isSetupInProgress = false
                        )
                    }
                    return@launch
                }
                
                // Step 3: Download bootstrap if needed
                if (needsDownload) {
                    _uiState.update { 
                        it.copy(
                            status = BootstrapStatus.Downloading,
                            progress = 20
                        )
                    }
                    
                    appendLog("Downloading bootstrap package...")
                    
                    val progressCallback = object : BootstrapDownloader.ProgressCallback {
                        override fun onProgress(downloaded: Long, total: Long) {
                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt()
                                if (percent != lastDownloadProgressPercent) {
                                    lastDownloadProgressPercent = percent
                                    val now = System.currentTimeMillis()
                                    if (now - lastDownloadLogUpdateTimeMs > 500) {
                                        lastDownloadLogUpdateTimeMs = now
                                        // Map download progress to 20-80% of total progress
                                        val mappedProgress = 20 + (percent * 60 / 100)
                                        updateProgress(mappedProgress)
                                        appendLog("Downloading: $percent%")
                                    }
                                }
                            }
                        }
                    }
                    
                    val downloadError = withContext(Dispatchers.IO) {
                        BootstrapManager.downloadBootstrap(
                            getApplication(),
                            arch,
                            progressCallback
                        )
                    }
                    
                    if (isCancelled) {
                        appendLog("Setup cancelled by user")
                        _uiState.update { 
                            it.copy(
                                status = BootstrapStatus.Cancelled,
                                isSetupInProgress = false
                            )
                        }
                        return@launch
                    }
                    
                    if (downloadError != null) {
                        val errorMsg = "Download failed: ${downloadError.message}"
                        Logger.logError(LOG_TAG, errorMsg)
                        throw Exception(errorMsg)
                    }
                    
                    // Update local file reference after download
                    val localFile = withContext(Dispatchers.IO) {
                        BootstrapManager.getLocalBootstrapFile(getApplication(), arch)
                    }
                    
                    _uiState.update {
                        it.copy(
                            localBootstrapFile = localFile,
                            progress = 80
                        )
                    }
                    
                    appendLog("Download completed successfully.")
                } else {
                    appendLog("Using existing local bootstrap file.")
                    _uiState.update { it.copy(progress = 80) }
                }
                
                // Step 4: Installation will be handled by Activity
                // The ViewModel just prepares the state
                _uiState.update {
                    it.copy(
                        status = BootstrapStatus.Installing,
                        progress = 85,
                        isSetupInProgress = true // Keep in progress during installation
                    )
                }
                appendLog("Ready for installation. Waiting for Activity to proceed...")
                
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error in automatic setup", e)
                val errorMsg = e.message ?: "Unknown error"
                showError(errorMsg)
                _uiState.update {
                    it.copy(
                        status = BootstrapStatus.Failed,
                        isSetupInProgress = false
                    )
                }
                appendLog("Setup failed: $errorMsg")
            }
        }
    }
    
    /**
     * Stops the current setup process.
     */
    fun stopSetup() {
        if (!_uiState.value.isSetupInProgress) {
            return
        }
        
        isCancelled = true
        setupJob?.cancel()
        appendLog("Setup stopped by user")
        _uiState.update {
            it.copy(
                status = BootstrapStatus.Cancelled,
                isSetupInProgress = false
            )
        }
    }
    
    /**
     * Reinstalls bootstrap by forcing a fresh download and installation.
     */
    fun reinstallBootstrap() {
        if (_uiState.value.isSetupInProgress) {
            stopSetup()
        }
        
        // Clear local bootstrap file to force re-download
        val arch = _uiState.value.detectedArch
        if (arch != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val localFile = BootstrapManager.getLocalBootstrapFile(getApplication(), arch)
                if (localFile != null && localFile.exists()) {
                    localFile.delete()
                    Logger.logInfo(LOG_TAG, "Deleted local bootstrap file for reinstall")
                }
            }
        }
        
        // Reset state and start fresh
        _uiState.update {
            BootstrapSetupUiState()
        }
        
        startAutomaticSetup()
    }
    
    /**
     * Skips bootstrap setup and navigates away.
     */
    fun skipSetup() {
        if (_uiState.value.isSetupInProgress) {
            stopSetup()
        }
        appendLog("Bootstrap setup skipped by user")
    }
    
    /**
     * Called when bootstrap installation completes successfully.
     */
    fun onBootstrapInstallComplete() {
        _uiState.update {
            it.copy(
                status = BootstrapStatus.Completed,
                progress = 100,
                isSetupInProgress = false,
                bootstrapAlreadyInstalled = true
            )
        }
        appendLog("Bootstrap installed successfully.")
    }
    
    /**
     * Called when bootstrap installation fails.
     */
    fun onBootstrapInstallError(error: String) {
        showError("Installation failed: $error")
        _uiState.update {
            it.copy(
                status = BootstrapStatus.Failed,
                isSetupInProgress = false
            )
        }
        appendLog("Bootstrap installation failed: $error")
    }
    
    fun appendLog(message: String) {
        Logger.logInfo(LOG_TAG, message)
        _uiState.update { currentState ->
            currentState.copy(logs = currentState.logs + message)
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
    
    private fun updateProgress(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _uiState.update { it.copy(progress = clamped) }
    }
}
