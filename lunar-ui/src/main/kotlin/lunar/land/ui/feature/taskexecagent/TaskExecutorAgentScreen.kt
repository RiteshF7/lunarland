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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    // Initialize service binding - ensure TermuxService is bound for terminal CLI access
    LaunchedEffect(Unit) {
        viewModel.bindService(context)
    }
    
    // Cleanup on dispose - properly unbind service to prevent leaks
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindService(context)
        }
    }
    
    // Dark grey background matching the image
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Center content - Icon and text in upper half
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Speech bubble icon with "UI" text
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "UI",
                        color = Color(0xFF2A2A2A),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // "Chatbot UI" text
                Text(
                    text = "Chatbot UI",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            
            // Input field at the bottom
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
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}
