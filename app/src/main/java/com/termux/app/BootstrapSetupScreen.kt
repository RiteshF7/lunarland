package com.termux.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.termux.app.bootstrap.BootstrapSetupViewModel
import com.termux.app.bootstrap.BootstrapStatus
import com.termux.shared.interact.ShareUtils
import lunar.land.ui.core.ui.*
import lunar.land.ui.core.ui.providers.ProvideSystemUiController
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.model.Theme

// Color constants for green/black theme
private val AGENT_GREEN = Color(0xFF00FF00)
private val AGENT_BLACK = Color(0xFF000000)
private val AGENT_DARK_GREEN = Color(0xFF00AA00)

/**
 * Agent Setup Screen with automatic detection and installation.
 * Shows progress bar, logs, and action buttons below logs.
 */
@Composable
fun BootstrapSetupScreen(
    viewModel: BootstrapSetupViewModel,
    onNavigateToHome: () -> Unit,
    onInstallBootstrap: ((() -> Unit), ((String) -> Unit)) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    // Provide SystemUiController and use lunar-ui theme
    ProvideSystemUiController {
        LauncherTheme(
            currentTheme = Theme.SAID_DARK,
            content = {
                Scaffold(
                    // No top bar - removed as requested
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AGENT_BLACK)
                            .padding(paddingValues)
                            .padding(24.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Status Text
                        StatusText(status = uiState.status)
                        
                        // Progress Indicator with green color
                        AgentProgressIndicator(
                            progress = uiState.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Error Banner with green/black theme
                        AgentErrorBanner(
                            error = uiState.error,
                            onRetry = { 
                                viewModel.clearError()
                                viewModel.startAutomaticSetup()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Log Viewer - moved above buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Logs:",
                                style = MaterialTheme.typography.titleMedium,
                                color = AGENT_GREEN
                            )
                            
                            // Copy Logs Button
                            AgentButton(
                                text = "Copy Logs",
                                onClick = {
                                    val logsText = uiState.logs.joinToString("\n")
                                    ShareUtils.copyTextToClipboard(
                                        context,
                                        logsText,
                                        "Logs copied to clipboard"
                                    )
                                },
                                modifier = Modifier.height(36.dp)
                            )
                        }
                        
                        AgentLogViewer(
                            logs = uiState.logs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Action Buttons Row - moved below logs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Stop Button (only show when in progress)
                            if (uiState.isSetupInProgress) {
                                AgentButton(
                                    text = "Stop",
                                    onClick = { viewModel.stopSetup() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            // Reinstall Button
                            AgentButton(
                                text = "Reinstall",
                                onClick = { viewModel.reinstallBootstrap() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isSetupInProgress
                            )
                            
                            // Skip Button
                            AgentButton(
                                text = "Skip",
                                onClick = { 
                                    viewModel.skipSetup()
                                    onNavigateToHome()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * Custom progress indicator with green/black theme
 */
@Composable
private fun AgentProgressIndicator(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0, 100)
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { clampedProgress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = AGENT_GREEN,
            trackColor = AGENT_DARK_GREEN.copy(alpha = 0.3f)
        )
        
        Text(
            text = "$clampedProgress%",
            style = MaterialTheme.typography.bodyMedium,
            color = AGENT_GREEN,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Custom error banner with green/black theme
 */
@Composable
private fun AgentErrorBanner(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = error != null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = AGENT_BLACK,
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = AGENT_GREEN,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = AGENT_GREEN,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AGENT_GREEN
                    )
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * Custom log viewer with green text on black background
 */
@Composable
private fun AgentLogViewer(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll to bottom when new logs are added
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = AGENT_BLACK,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = AGENT_GREEN,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            logs.forEachIndexed { index, log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                    color = AGENT_GREEN,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index < logs.size - 1) Modifier.padding(bottom = 4.dp) else Modifier)
                )
            }
            
            // Empty state
            if (logs.isEmpty()) {
                Text(
                    text = "No logs yet...",
                    style = MaterialTheme.typography.bodySmall,
                    color = AGENT_GREEN.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Custom button with green/black theme
 */
@Composable
private fun AgentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                color = if (enabled) AGENT_DARK_GREEN else AGENT_DARK_GREEN.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (enabled) AGENT_GREEN else AGENT_GREEN.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) AGENT_BLACK else AGENT_BLACK.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Displays status text based on current agent setup status.
 */
@Composable
private fun StatusText(status: BootstrapStatus) {
    val statusText = when (status) {
        BootstrapStatus.Idle -> "Initializing..."
        BootstrapStatus.Detecting -> "Detecting agent environment..."
        BootstrapStatus.Downloading -> "Downloading agent environment..."
        BootstrapStatus.Installing -> "Setting up agent environment..."
        BootstrapStatus.Completed -> "Agent environment setup completed!"
        BootstrapStatus.Failed -> "Agent environment setup failed"
        BootstrapStatus.Cancelled -> "Agent environment setup cancelled"
    }
    
    Text(
        text = statusText,
        style = MaterialTheme.typography.titleLarge,
        color = AGENT_GREEN
    )
}
