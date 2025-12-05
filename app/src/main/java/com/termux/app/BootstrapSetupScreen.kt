package com.termux.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termux.app.bootstrap.BootstrapSetupViewModel
import com.termux.app.bootstrap.BootstrapStatus
import com.termux.R
import lunar.land.ui.core.ui.*
import lunar.land.ui.core.ui.providers.ProvideSystemUiController
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.model.Theme

/**
 * Bootstrap Setup Screen with automatic detection and installation.
 * Shows progress bar and action buttons (Stop, Reinstall, Skip).
 */
@Composable
fun BootstrapSetupScreen(
    viewModel: BootstrapSetupViewModel,
    onNavigateToHome: () -> Unit,
    onInstallBootstrap: ((() -> Unit), ((String) -> Unit)) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Provide SystemUiController and use lunar-ui theme
    ProvideSystemUiController {
        LauncherTheme(
            currentTheme = Theme.SAID_DARK,
            content = {
                Scaffold(
                    topBar = {
                        AppBarWithBackIcon(
                            title = stringResource(R.string.bootstrap_setup_title),
                            onBackPressed = onNavigateToHome
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Status Text
                        StatusText(status = uiState.status)
                        
                        // Progress Indicator
                        ProgressIndicator(
                            progress = uiState.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Error Banner
                        ErrorBanner(
                            error = uiState.error,
                            onRetry = { 
                                viewModel.clearError()
                                viewModel.startAutomaticSetup()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Stop Button (only show when in progress)
                            if (uiState.isSetupInProgress) {
                                OutlinedButton(
                                    onClick = { viewModel.stopSetup() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Stop")
                                }
                            }
                            
                            // Reinstall Button
                            OutlinedButton(
                                onClick = { viewModel.reinstallBootstrap() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isSetupInProgress || uiState.status == BootstrapStatus.Completed
                            ) {
                                Text("Reinstall")
                            }
                            
                            // Skip Button
                            OutlinedButton(
                                onClick = { 
                                    viewModel.skipSetup()
                                    onNavigateToHome()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Skip")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Log Viewer
                        Text(
                            text = "Logs:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        LogViewer(
                            logs = uiState.logs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        )
                    }
                }
            }
        )
    }
}

/**
 * Displays status text based on current bootstrap status.
 */
@Composable
private fun StatusText(status: BootstrapStatus) {
    val statusText = when (status) {
        BootstrapStatus.Idle -> "Initializing..."
        BootstrapStatus.Detecting -> "Detecting bootstrap status..."
        BootstrapStatus.Downloading -> "Downloading bootstrap..."
        BootstrapStatus.Installing -> "Installing bootstrap..."
        BootstrapStatus.Completed -> "Bootstrap setup completed!"
        BootstrapStatus.Failed -> "Bootstrap setup failed"
        BootstrapStatus.Cancelled -> "Bootstrap setup cancelled"
    }
    
    Text(
        text = statusText,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}
