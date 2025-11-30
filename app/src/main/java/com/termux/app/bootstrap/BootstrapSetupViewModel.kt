package com.termux.app.bootstrap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.app.bootstrap.BootstrapArchDetector
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import com.termux.shared.termux.file.TermuxFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lunar.land.ui.core.ui.StepButtonState
import java.io.File

data class BootstrapSetupUiState(
    val logs: List<String> = listOf("Select steps to perform or skip. Setup will not start automatically."),
    val progress: Int = 0,
    val error: String? = null,
    val isSetupInProgress: Boolean = false,
    
    // Step states
    val stepCheckBootstrap: StepButtonState = StepButtonState.Idle,
    val stepDetectArch: StepButtonState = StepButtonState.Idle,
    val stepCheckLocal: StepButtonState = StepButtonState.Idle,
    val stepDownload: StepButtonState = StepButtonState.Idle,
    val stepInstall: StepButtonState = StepButtonState.Idle,
    
    // Step data
    val detectedArch: String? = null,
    val localBootstrapFile: File? = null,
    val bootstrapAlreadyInstalled: Boolean = false
)

class BootstrapSetupViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(BootstrapSetupUiState())
    val uiState: StateFlow<BootstrapSetupUiState> = _uiState.asStateFlow()
    
    private val LOG_TAG = "BootstrapSetupViewModel"
    
    // Throttling for progress updates
    private var lastDownloadProgressPercent = 0
    private var lastDownloadLogUpdateTimeMs = 0L
    
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
    
    fun checkBootstrap() {
        if (_uiState.value.isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress")
            return
        }
        
        _uiState.update { 
            it.copy(
                isSetupInProgress = true,
                error = null,
                stepCheckBootstrap = StepButtonState.Running
            )
        }
        appendLog("Checking if bootstrap is already installed...")
        
        viewModelScope.launch {
            try {
                val installed = withContext(Dispatchers.IO) {
                    TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false) == null &&
                    !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()
                }
                
                _uiState.update { 
                    it.copy(
                        bootstrapAlreadyInstalled = installed,
                        stepCheckBootstrap = if (installed) {
                            StepButtonState.Completed("Bootstrap is already installed.")
                        } else {
                            StepButtonState.Completed("Bootstrap is not installed.")
                        },
                        isSetupInProgress = false
                    )
                }
                
                if (installed) {
                    appendLog("Bootstrap is already installed.")
                } else {
                    appendLog("Bootstrap is not installed.")
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error checking bootstrap", e)
                showError("Error checking bootstrap: ${e.message}")
                _uiState.update { 
                    it.copy(
                        stepCheckBootstrap = StepButtonState.Failed(e.message ?: "Unknown error"),
                        isSetupInProgress = false
                    )
                }
            }
        }
    }
    
    fun skipCheckBootstrap() {
        appendLog("Skipped: Check Bootstrap")
        _uiState.update { it.copy(stepCheckBootstrap = StepButtonState.Skipped) }
    }
    
    fun rerunCheckBootstrap() {
        appendLog("Rerunning: Check Bootstrap")
        _uiState.update { it.copy(stepCheckBootstrap = StepButtonState.Idle) }
        checkBootstrap()
    }
    
    fun detectArch() {
        if (_uiState.value.isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress")
            return
        }
        
        _uiState.update { 
            it.copy(
                isSetupInProgress = true,
                error = null,
                stepDetectArch = StepButtonState.Running
            )
        }
        appendLog("Detecting device architecture...")
        
        viewModelScope.launch {
            try {
                val arch = withContext(Dispatchers.IO) {
                    BootstrapArchDetector.detectArch()
                }
                
                _uiState.update { 
                    it.copy(
                        detectedArch = arch,
                        stepDetectArch = StepButtonState.Completed("Detected architecture: $arch"),
                        isSetupInProgress = false
                    )
                }
                appendLog("Detected architecture: $arch")
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error detecting architecture", e)
                showError("Error detecting architecture: ${e.message}")
                _uiState.update { 
                    it.copy(
                        stepDetectArch = StepButtonState.Failed(e.message ?: "Unknown error"),
                        isSetupInProgress = false
                    )
                }
            }
        }
    }
    
    fun skipDetectArch() {
        appendLog("Skipped: Detect Architecture")
        _uiState.update { it.copy(stepDetectArch = StepButtonState.Skipped) }
    }
    
    fun rerunDetectArch() {
        appendLog("Rerunning: Detect Architecture")
        _uiState.update { it.copy(stepDetectArch = StepButtonState.Idle) }
        detectArch()
    }
    
    fun checkLocal() {
        if (_uiState.value.isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress")
            return
        }
        
        val arch = _uiState.value.detectedArch
        if (arch == null) {
            showError("Please detect architecture first")
            return
        }
        
        _uiState.update { 
            it.copy(
                isSetupInProgress = true,
                error = null,
                stepCheckLocal = StepButtonState.Running
            )
        }
        appendLog("Checking for local bootstrap file...")
        
        viewModelScope.launch {
            try {
                val localFile = withContext(Dispatchers.IO) {
                    BootstrapDownloader.getLocalBootstrapFile(getApplication(), arch)
                }
                
                _uiState.update { 
                    it.copy(
                        localBootstrapFile = localFile,
                        stepCheckLocal = if (localFile != null && localFile.exists() && localFile.length() > 0) {
                            StepButtonState.Completed("Local bootstrap file found")
                        } else {
                            StepButtonState.Completed("No local bootstrap file found")
                        },
                        isSetupInProgress = false
                    )
                }
                
                if (localFile != null && localFile.exists() && localFile.length() > 0) {
                    appendLog("Local bootstrap file found: ${localFile.absolutePath}")
                } else {
                    appendLog("No local bootstrap file found.")
                }
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error checking local file", e)
                showError("Error checking local file: ${e.message}")
                _uiState.update { 
                    it.copy(
                        stepCheckLocal = StepButtonState.Failed(e.message ?: "Unknown error"),
                        isSetupInProgress = false
                    )
                }
            }
        }
    }
    
    fun skipCheckLocal() {
        appendLog("Skipped: Check Local File")
        _uiState.update { it.copy(stepCheckLocal = StepButtonState.Skipped) }
    }
    
    fun rerunCheckLocal() {
        appendLog("Rerunning: Check Local File")
        _uiState.update { it.copy(stepCheckLocal = StepButtonState.Idle) }
        checkLocal()
    }
    
    fun downloadBootstrap() {
        if (_uiState.value.isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress")
            return
        }
        
        val arch = _uiState.value.detectedArch
        if (arch == null) {
            showError("Please detect architecture first")
            return
        }
        
        _uiState.update { 
            it.copy(
                isSetupInProgress = true,
                error = null,
                stepDownload = StepButtonState.Running,
                progress = 0
            )
        }
        appendLog("Downloading bootstrap package...")
        
        viewModelScope.launch {
            try {
                val progressCallback = object : BootstrapDownloader.ProgressCallback {
                    override fun onProgress(downloaded: Long, total: Long) {
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent != lastDownloadProgressPercent) {
                                lastDownloadProgressPercent = percent
                                val now = System.currentTimeMillis()
                                if (now - lastDownloadLogUpdateTimeMs > 500) {
                                    lastDownloadLogUpdateTimeMs = now
                                    updateProgress(percent)
                                    appendLog("Downloading: $percent%")
                                }
                            }
                        }
                    }
                }
                
                val downloadError = withContext(Dispatchers.IO) {
                    BootstrapDownloader.downloadBootstrap(getApplication(), arch, progressCallback)
                }
                
                if (downloadError != null) {
                    val errorMsg = "Download failed: ${downloadError.message}"
                    Logger.logError(LOG_TAG, errorMsg)
                    showError(errorMsg)
                    _uiState.update { 
                        it.copy(
                            stepDownload = StepButtonState.Failed(downloadError.message ?: "Unknown error"),
                            isSetupInProgress = false
                        )
                    }
                    return@launch
                }
                
                val localFile = withContext(Dispatchers.IO) {
                    BootstrapDownloader.getLocalBootstrapFile(getApplication(), arch)
                }
                
                _uiState.update { 
                    it.copy(
                        localBootstrapFile = localFile,
                        stepDownload = StepButtonState.Completed("Download completed successfully"),
                        progress = 100,
                        isSetupInProgress = false
                    )
                }
                appendLog("Download completed successfully.")
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error downloading bootstrap", e)
                showError("Error downloading bootstrap: ${e.message}")
                _uiState.update { 
                    it.copy(
                        stepDownload = StepButtonState.Failed(e.message ?: "Unknown error"),
                        isSetupInProgress = false
                    )
                }
            }
        }
    }
    
    fun skipDownload() {
        appendLog("Skipped: Download Bootstrap")
        _uiState.update { it.copy(stepDownload = StepButtonState.Skipped) }
    }
    
    fun rerunDownload() {
        appendLog("Rerunning: Download Bootstrap")
        _uiState.update { it.copy(stepDownload = StepButtonState.Idle) }
        downloadBootstrap()
    }
    
    fun installBootstrap(onInstallComplete: () -> Unit) {
        if (_uiState.value.isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress")
            return
        }
        
        val localFile = _uiState.value.localBootstrapFile
        if (localFile == null || !localFile.exists()) {
            showError("Please download bootstrap first")
            return
        }
        
        _uiState.update { 
            it.copy(
                isSetupInProgress = true,
                error = null,
                stepInstall = StepButtonState.Running,
                progress = 92
            )
        }
        appendLog("Installing bootstrap package...")
        appendLog("Extracting bootstrap archive...")
        
        // Note: TermuxInstaller.setupBootstrapIfNeeded requires Activity context
        // We'll need to handle this in the Activity/Composable
        // For now, we'll prepare the state and let the Activity handle the actual installation
        _uiState.update { 
            it.copy(
                stepInstall = StepButtonState.Running,
                progress = 92
            )
        }
    }
    
    fun onBootstrapInstallComplete() {
        _uiState.update { 
            it.copy(
                stepInstall = StepButtonState.Completed("Bootstrap installed successfully"),
                progress = 100,
                isSetupInProgress = false
            )
        }
        appendLog("Bootstrap installed successfully.")
    }
    
    fun onBootstrapInstallError(error: String) {
        showError("Error installing bootstrap: $error")
        _uiState.update { 
            it.copy(
                stepInstall = StepButtonState.Failed(error),
                isSetupInProgress = false
            )
        }
    }
    
    fun skipInstall() {
        appendLog("Skipped: Install Bootstrap")
        _uiState.update { it.copy(stepInstall = StepButtonState.Skipped) }
    }
    
    fun rerunInstall() {
        appendLog("Rerunning: Install Bootstrap")
        _uiState.update { it.copy(stepInstall = StepButtonState.Idle) }
    }
}

