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
import com.termux.R
import lunar.land.ui.core.ui.*
import lunar.land.ui.core.ui.providers.ProvideSystemUiController
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.model.Theme

/**
 * Main Bootstrap Setup Screen using Compose with Flow, ViewModel, and Coroutines
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
                        // Progress Indicator
                        ProgressIndicator(
                            progress = uiState.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Error Banner
                        ErrorBanner(
                            error = uiState.error,
                            onRetry = { viewModel.clearError() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Setup Steps Section
                        Text(
                            text = "Setup Steps:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // Step 1: Check Bootstrap
                        StepButton(
                            text = "1. Check Bootstrap",
                            state = uiState.stepCheckBootstrap,
                            enabled = !uiState.isSetupInProgress,
                            onRun = { viewModel.checkBootstrap() },
                            onSkip = { viewModel.skipCheckBootstrap() },
                            onRerun = { viewModel.rerunCheckBootstrap() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Step 2: Detect Architecture
                        StepButton(
                            text = "2. Detect Architecture",
                            state = uiState.stepDetectArch,
                            enabled = !uiState.isSetupInProgress,
                            onRun = { viewModel.detectArch() },
                            onSkip = { viewModel.skipDetectArch() },
                            onRerun = { viewModel.rerunDetectArch() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Step 3: Check Local File
                        StepButton(
                            text = "3. Check Local File",
                            state = uiState.stepCheckLocal,
                            enabled = !uiState.isSetupInProgress && uiState.detectedArch != null,
                            onRun = { viewModel.checkLocal() },
                            onSkip = { viewModel.skipCheckLocal() },
                            onRerun = { viewModel.rerunCheckLocal() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Step 4: Download Bootstrap
                        StepButton(
                            text = "4. Download Bootstrap",
                            state = uiState.stepDownload,
                            enabled = !uiState.isSetupInProgress && uiState.detectedArch != null,
                            onRun = { viewModel.downloadBootstrap() },
                            onSkip = { viewModel.skipDownload() },
                            onRerun = { viewModel.rerunDownload() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Step 5: Install Bootstrap
                        StepButton(
                            text = "5. Install Bootstrap",
                            state = uiState.stepInstall,
                            enabled = !uiState.isSetupInProgress && uiState.localBootstrapFile != null,
                            onRun = { 
                                viewModel.installBootstrap {
                                    // Installation will be handled via callback
                                }
                                // Trigger actual installation via callback provided by Activity
                                onInstallBootstrap(
                                    { viewModel.onBootstrapInstallComplete() },
                                    { error -> viewModel.onBootstrapInstallError(error) }
                                )
                            },
                            onSkip = { viewModel.skipInstall() },
                            onRerun = { viewModel.rerunInstall() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
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
                        
                        // Home Button
                        ActionButton(
                            text = "Go to Home",
                            onClick = onNavigateToHome,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        )
    }
}
