package com.termux.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import com.termux.app.bootstrap.BootstrapSetupViewModel
import com.termux.shared.logger.Logger
import com.termux.app.TermuxInstaller

/**
 * Activity responsible for downloading and installing bootstrap packages.
 * Uses Compose UI with ViewModel, Flow, and Coroutines.
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
                    installBootstrapWithLunarAgentSetup(onComplete, onError)
                }
            )
        }
    }
    
    /**
     * Triggers bootstrap installation using TermuxInstaller.
     * This method requires Activity context, so it's handled here rather than in ViewModel.
     */
    private fun installBootstrapWithLunarAgentSetup(
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        Logger.logInfo(LOG_TAG, "Starting bootstrap installation...")
        
        TermuxInstaller.setupBootstrapIfNeeded(this) {
            try {
                Logger.logInfo(LOG_TAG, "Bootstrap installation completed successfully")
                onComplete()
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error in bootstrap installation callback", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun navigateToHome() {
        val intent = Intent(this, DriverActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

