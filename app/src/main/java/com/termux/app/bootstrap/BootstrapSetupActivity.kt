package com.termux.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.termux.app.bootstrap.BootstrapSetupViewModel
import com.termux.app.bootstrap.BootstrapStatus
import com.termux.app.bootstrap.BootstrapManager
import com.termux.shared.logger.Logger
import com.termux.app.TermuxInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Activity responsible for downloading and installing bootstrap packages.
 * Uses Compose UI with ViewModel, Flow, and Coroutines.
 * Automatically detects and starts bootstrap setup process.
 */
class BootstrapSetupActivity : ComponentActivity() {
    private val LOG_TAG = "BootstrapSetupActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel: BootstrapSetupViewModel = viewModel()
            
            BootstrapSetupScreen(
                viewModel = viewModel,
                onNavigateToHome = {
                    navigateToHome()
                },
                onInstallBootstrap = { onComplete, onError ->
                    installBootstrapWithLunarAgentSetup(viewModel, onComplete, onError)
                }
            )
            
            // Automatically trigger installation when status is Installing
            AutoInstallTrigger(viewModel)
        }
    }
    
    /**
     * Composable that watches for installation status and triggers installation automatically.
     */
    @Composable
    private fun AutoInstallTrigger(viewModel: BootstrapSetupViewModel) {
        val uiState by viewModel.uiState.collectAsState()
        var installationTriggered by remember { mutableStateOf(false) }
        
        LaunchedEffect(uiState.status, uiState.localBootstrapFile, uiState.bootstrapAlreadyInstalled) {
            // Only proceed if bootstrap is not already installed
            if (uiState.bootstrapAlreadyInstalled) {
                Logger.logInfo(LOG_TAG, "Agent environment already installed, skipping installation")
                return@LaunchedEffect
            }
            
            if (uiState.status == BootstrapStatus.Installing && 
                uiState.localBootstrapFile != null &&
                !installationTriggered) {
                installationTriggered = true
                Logger.logInfo(LOG_TAG, "Auto-triggering agent environment installation...")
                
                installBootstrapWithLunarAgentSetup(
                    viewModel,
                    onComplete = {
                        viewModel.onBootstrapInstallComplete()
                        installationTriggered = false
                    },
                    onError = { error ->
                        viewModel.onBootstrapInstallError(error)
                        installationTriggered = false
                    }
                )
            }
            
            // Reset trigger if status changes away from Installing
            if (uiState.status != BootstrapStatus.Installing) {
                installationTriggered = false
            }
        }
    }
    
    /**
     * Triggers agent environment installation using TermuxInstaller.
     * This method requires Activity context, so it's handled here rather than in ViewModel.
     */
    private fun installBootstrapWithLunarAgentSetup(
        viewModel: BootstrapSetupViewModel,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        Logger.logInfo(LOG_TAG, "Starting agent environment installation...")
        viewModel.appendLog("Starting agent environment installation...")
        
        val logCallback = TermuxInstaller.LogCallback { message ->
            viewModel.appendLog(message)
        }
        
        TermuxInstaller.setupBootstrapIfNeeded(this, {
            try {
                val successMsg = "Agent environment installation completed successfully"
                Logger.logInfo(LOG_TAG, successMsg)
                viewModel.appendLog(successMsg)
                
                // Delete bootstrap zip file after installation
                deleteBootstrapZipFile(viewModel)
                
                // Mark bootstrap installation as complete
                viewModel.onBootstrapInstallComplete()
                
                onComplete()
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Logger.logStackTraceWithMessage(LOG_TAG, "Error in agent environment installation callback", e)
                viewModel.appendLog("Error: $errorMsg")
                onError(errorMsg)
            }
        }, logCallback)
    }
    
    /**
     * Deletes the bootstrap zip file after successful installation.
     */
    private fun deleteBootstrapZipFile(viewModel: BootstrapSetupViewModel) {
        try {
            // Get architecture from ViewModel state or detect it
            val arch = viewModel.uiState.value.detectedArch 
                ?: BootstrapManager.detectBootstrap(this).architecture
                ?: return
            
            val bootstrapDir = File(filesDir, "bootstraps")
            val bootstrapFile = File(bootstrapDir, "bootstrap-$arch.zip")
            
            if (bootstrapFile.exists()) {
                val deleted = bootstrapFile.delete()
                if (deleted) {
                    Logger.logInfo(LOG_TAG, "Deleted bootstrap zip file: ${bootstrapFile.absolutePath}")
                    viewModel.appendLog("Cleaned up bootstrap installation files")
                } else {
                    Logger.logWarn(LOG_TAG, "Failed to delete bootstrap zip file")
                }
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error deleting bootstrap zip file: ${e.message}")
        }
    }
    
    private fun navigateToHome() {
        val intent = Intent(this, com.termux.app.driver.DriverActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
