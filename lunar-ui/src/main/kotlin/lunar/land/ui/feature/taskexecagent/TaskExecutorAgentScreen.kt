package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.termux.app.TaskExecutorViewModel
import com.termux.app.taskexecutor.model.TaskStatus
import lunar.land.ui.core.theme.LunarTheme

/**
 * Main screen composable for the AI Agent Interaction Sphere.
 * This is the entry point that orchestrates all the UI components.
 */
@Composable
fun TaskExecutorAgentScreen(
    modifier: Modifier = Modifier,
    viewModel: TaskExecutorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var isTextInputFocused by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Initialize service binding
    LaunchedEffect(Unit) {
        if (context is android.app.Activity) {
            viewModel.bindService(context)
        }
    }
    
    // Cleanup on dispose
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (context is android.app.Activity) {
                viewModel.unbindService(context)
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LunarTheme.BackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LunarTheme.Spacing.ExtraLarge, vertical = LunarTheme.Spacing.XXLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section with Debug Logs Button
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                PageHeader(
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Debug logs button in top-right corner
                DebugLogsButton(
                    showLogs = uiState.showLogs,
                    onToggle = { viewModel.toggleLogsVisibility() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                )
            }
            
            // Logs view (conditionally visible)
            AnimatedVisibility(
                visible = uiState.showLogs,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut() + androidx.compose.animation.scaleOut()
            ) {
                LogsView(
                    outputText = uiState.outputText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // Main Content Area - Always show sphere
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Show text input above sphere (only when not running)
                if (!uiState.isTaskRunning) {
                    TextInputPanel(
                        onExecute = { command ->
                            if (command.isNotBlank() && !uiState.isTaskRunning) {
                                viewModel.dispatchCommand(command)
                            }
                        },
                        onFocusChange = { focused ->
                            isTextInputFocused = focused
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 24.dp)
                    )
                }
                
                // Always show sphere
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SphereVisualizer(
                        modifier = Modifier.size(200.dp),
                        taskStatus = uiState.taskStatus,
                        isTaskRunning = uiState.isTaskRunning,
                        stateMessage = uiState.agentStateMessage,
                        onStopTask = {
                            if (uiState.isTaskRunning) {
                                viewModel.stopCurrentTask()
                            }
                        }
                    )
                }
            }

            // Footer Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status text - use agentStateMessage from state manager
                StatusText(
                    text = uiState.agentStateMessage.ifEmpty { 
                        when (uiState.taskStatus) {
                            TaskStatus.STOPPED -> "Ready"
                            TaskStatus.RUNNING -> "Running..."
                            TaskStatus.SUCCESS -> "Task completed successfully"
                            TaskStatus.ERROR -> "An error occurred"
                        }
                    }
                )
                
                PageFooter(
                    modifier = Modifier.fillMaxWidth(),
                    aiStatus = uiState.statusText.ifEmpty { "Ready" }
                )
            }
        }
    }
}
