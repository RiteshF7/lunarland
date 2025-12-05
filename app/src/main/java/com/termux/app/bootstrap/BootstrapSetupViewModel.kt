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
    private var droidrunDownloadJob: Job? = null
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
        appendLog("Starting automatic agent environment setup...")
        
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
                
                // Step 1: Detect agent environment status and architecture
                appendLog("Detecting agent environment status and architecture...")
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
                    appendLog("Agent environment is already installed.")
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
                
                // Step 3: Download agent environment if needed
                if (needsDownload) {
                    _uiState.update { 
                        it.copy(
                            status = BootstrapStatus.Downloading,
                            progress = 20
                        )
                    }
                    
                    appendLog("Downloading agent environment package...")
                    
                    val progressCallback = object : BootstrapDownloader.ProgressCallback {
                        override fun onProgress(downloaded: Long, total: Long) {
                            // Check for cancellation during download
                            if (isCancelled) {
                                throw kotlinx.coroutines.CancellationException("Download cancelled by user")
                            }
                            
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
                    
                    val downloadError = try {
                        withContext(Dispatchers.IO) {
                            // Check cancellation before starting download
                            if (isCancelled) {
                                throw kotlinx.coroutines.CancellationException("Download cancelled by user")
                            }
                            BootstrapManager.downloadBootstrap(
                                getApplication(),
                                arch,
                                progressCallback
                            )
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Re-throw cancellation to be handled by outer catch
                        throw e
                    } catch (e: Exception) {
                        Error(com.termux.shared.errors.Errno.TYPE, com.termux.shared.errors.Errno.ERRNO_FAILED.code, "Download error: ${e.message}")
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
                    appendLog("Using existing local agent environment file.")
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
                appendLog("Ready for installation. Setting up agent environment...")
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully
                appendLog("Setup cancelled by user")
                _uiState.update {
                    it.copy(
                        status = BootstrapStatus.Cancelled,
                        isSetupInProgress = false
                    )
                }
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
        
        appendLog("Stopping setup...")
        isCancelled = true
        setupJob?.cancel()
        
        // Also cancel droidrun download if in progress
        droidrunDownloadJob?.cancel()
        
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
        
        // Reset state first
        _uiState.update {
            BootstrapSetupUiState()
        }
        
        appendLog("Starting reinstall of agent environment...")
        appendLog("Deleting installed agent environment...")
        
        viewModelScope.launch(Dispatchers.IO) {
            // First, delete the installed bootstrap (prefix directory)
            val deleteError = BootstrapManager.deleteInstalledBootstrap()
            if (deleteError != null) {
                withContext(Dispatchers.Main) {
                    val errorMsg = "Failed to delete installed agent environment: ${deleteError.message}"
                    Logger.logError(LOG_TAG, errorMsg)
                    appendLog(errorMsg)
                    showError(errorMsg)
                }
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                appendLog("Successfully deleted installed agent environment")
            }
            
            // Also delete local bootstrap file to force re-download
            val arch = _uiState.value.detectedArch ?: run {
                val detectionResult = BootstrapManager.detectBootstrap(getApplication())
                detectionResult.architecture
            }
            
            if (arch != null) {
                val localFile = BootstrapManager.getLocalBootstrapFile(getApplication(), arch)
                if (localFile != null && localFile.exists()) {
                    val deleted = localFile.delete()
                    Logger.logInfo(LOG_TAG, "Deleted local bootstrap file for reinstall: $deleted")
                    withContext(Dispatchers.Main) {
                        appendLog("Deleted local agent environment file for reinstall")
                    }
                }
            }
            
            // Start fresh setup after deletion
            withContext(Dispatchers.Main) {
                appendLog("Starting fresh agent environment setup...")
                startAutomaticSetup()
            }
        }
    }
    
    /**
     * Skips agent environment setup and navigates away.
     */
    fun skipSetup() {
        if (_uiState.value.isSetupInProgress) {
            stopSetup()
        }
        appendLog("Agent environment setup skipped by user")
    }
    
    /**
     * Called when agent environment installation completes successfully.
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
        appendLog("Agent environment installed successfully.")
        
        // Download droidrun dependency after bootstrap installation
        downloadDroidrunDependency()
    }
    
    /**
     * Downloads droidrun dependency from GitHub releases after bootstrap installation.
     */
    fun downloadDroidrunDependency() {
        val arch = _uiState.value.detectedArch
        if (arch == null) {
            appendLog("Warning: Cannot download droidrun dependency - architecture not detected")
            return
        }
        
        appendLog("Starting droidrun dependency download for architecture: $arch")
        
        droidrunDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var lastProgressUpdate = 0L
                val progressCallback: (Long, Long) -> Unit = { downloaded, total ->
                    if (total > 0) {
                        val percent = ((downloaded * 100) / total).toInt()
                        val now = System.currentTimeMillis()
                        // Throttle progress updates to avoid too many UI updates
                        if (now - lastProgressUpdate > 500) {
                            lastProgressUpdate = now
                            // Post log message to main thread
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                appendLog("Downloading droidrun dependency: $percent%")
                            }
                        }
                    }
                }
                
                val error = DroidrunDownloader.downloadAndExtractDroidrun(
                    getApplication(),
                    arch,
                    progressCallback,
                    onLogMessage = { message ->
                        // Post log message to main thread
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            appendLog(message)
                        }
                    },
                    isCancelled = { isCancelled }
                )
                
                withContext(Dispatchers.Main) {
                    if (error != null) {
                        val errorMsg = "Failed to download droidrun dependency: ${error.message}"
                        Logger.logError(LOG_TAG, errorMsg)
                        appendLog(errorMsg)
                        // Don't fail the entire setup if droidrun download fails
                    } else {
                        appendLog("Droidrun dependency downloaded and extracted successfully to ~/wheels")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    appendLog("Droidrun dependency download cancelled")
                }
            }
        }
    }
    
    /**
     * Called when agent environment installation fails.
     */
    fun onBootstrapInstallError(error: String) {
        showError("Installation failed: $error")
        _uiState.update {
            it.copy(
                status = BootstrapStatus.Failed,
                isSetupInProgress = false
            )
        }
        appendLog("Agent environment installation failed: $error")
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
