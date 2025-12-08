package lunar.land.ui.feature.taskexecagent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * Main screen composable for the AI Agent Interaction Sphere.
 * This is the entry point that orchestrates all the UI components.
 */
@Composable
fun TaskExecutorAgentScreen(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    aiStatus: String = "Online",
    onTextModeClick: () -> Unit = {},
    onExecuteCommand: (String) -> Unit = {}
) {
    var isTextMode by remember { mutableStateOf(false) }
    var isTextInputFocused by remember { mutableStateOf(false) }
    
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
            // Header Section with Mode Toggle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PageHeader(
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Mode Toggle Button
                ModeToggleButton(
                    isTextMode = isTextMode,
                    onToggle = { 
                        isTextMode = !isTextMode
                        // Reset focus state when switching modes
                        isTextInputFocused = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isTextMode) {
                    // Text mode: Show sphere when not typing, hide when typing
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Show sphere when text input is not focused (to show agent state)
                        AnimatedVisibility(
                            visible = !isTextInputFocused,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut() + scaleOut(targetScale = 0.8f),
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            SphereVisualizer(
                                modifier = Modifier.size(200.dp),
                                isListening = isListening
                            )
                        }
                        
                        // Text Input Panel
                        TextInputPanel(
                            onExecute = { command ->
                                onExecuteCommand(command)
                            },
                            onFocusChange = { focused ->
                                isTextInputFocused = focused
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                    }
                } else {
                    // Voice mode: Always show sphere
                    SphereVisualizer(
                        modifier = Modifier.size(280.dp),
                        isListening = isListening
                    )
                }
            }

            // Footer Section
            PageFooter(
                modifier = Modifier.fillMaxWidth(),
                aiStatus = aiStatus,
                isTextMode = isTextMode,
                onTextModeClick = onTextModeClick
            )
        }
    }
}



