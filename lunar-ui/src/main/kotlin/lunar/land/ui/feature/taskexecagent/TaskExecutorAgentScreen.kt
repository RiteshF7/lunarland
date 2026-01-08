package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.TaskExecutorViewModel
import com.termux.app.taskexecutor.model.TaskStatus

/**
 * Chat interface for Task Executor Agent.
 * Displays user commands, system messages, and command output in a chat-like interface.
 */
@Composable
fun TaskExecutorAgentScreen(
    modifier: Modifier = Modifier,
    viewModel: TaskExecutorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Chat message state
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var lastOutputText by remember { mutableStateOf("") }
    var lastStatusText by remember { mutableStateOf("") }
    var lastCurrentTask by remember { mutableStateOf<String?>(null) }
    
    // Initialize service binding
    LaunchedEffect(Unit) {
        viewModel.bindService(context)
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindService(context)
        }
    }
    
    // Track status changes and add as system messages
    LaunchedEffect(uiState.statusText) {
        if (uiState.statusText.isNotBlank() && uiState.statusText != lastStatusText && uiState.statusText != "Ready") {
            lastStatusText = uiState.statusText
            chatMessages = chatMessages + ChatMessage(
                text = uiState.statusText,
                type = MessageType.SYSTEM
            )
        }
    }
    
    // Track current task changes
    LaunchedEffect(uiState.currentTask) {
        if (uiState.currentTask != null && uiState.currentTask != lastCurrentTask) {
            lastCurrentTask = uiState.currentTask
            chatMessages = chatMessages + ChatMessage(
                text = "Executing: ${uiState.currentTask}",
                type = MessageType.SYSTEM
            )
        } else if (uiState.currentTask == null) {
            lastCurrentTask = null
        }
    }
    
    // Track output changes and add as output messages
    LaunchedEffect(uiState.outputText) {
        if (uiState.outputText != lastOutputText) {
            val newOutput = when {
                lastOutputText.isBlank() -> uiState.outputText
                uiState.outputText.length > lastOutputText.length && 
                uiState.outputText.startsWith(lastOutputText) -> {
                    uiState.outputText.substring(lastOutputText.length)
                }
                else -> uiState.outputText
            }
            
            if (newOutput.isNotBlank()) {
                val trimmedOutput = newOutput.trim()
                if (trimmedOutput.isNotBlank()) {
                    chatMessages = chatMessages + ChatMessage(
                        text = trimmedOutput,
                        type = MessageType.OUTPUT
                    )
                }
            }
            lastOutputText = uiState.outputText
        }
    }
    
    // Track task status changes
    LaunchedEffect(uiState.taskStatus) {
        when (uiState.taskStatus) {
            TaskStatus.SUCCESS -> {
                chatMessages = chatMessages + ChatMessage(
                    text = "✓ Task completed successfully",
                    type = MessageType.SYSTEM
                )
            }
            TaskStatus.ERROR -> {
                chatMessages = chatMessages + ChatMessage(
                    text = "✗ Task failed or error occurred",
                    type = MessageType.ERROR
                )
            }
            TaskStatus.STOPPED -> {
                if (uiState.isTaskRunning) {
                    chatMessages = chatMessages + ChatMessage(
                        text = "Task stopped",
                        type = MessageType.SYSTEM
                    )
                }
            }
            else -> {}
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
            // Chat messages area
            if (chatMessages.isNotEmpty()) {
                ChatMessageList(
                    messages = chatMessages,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                // Empty state - Icon and text in center
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
            }
            
            // Input field at the bottom - always visible
            TextInputPanel(
                onExecute = { command ->
                    if (command.isNotBlank() && !uiState.isTaskRunning) {
                        chatMessages = chatMessages + ChatMessage(
                            text = command,
                            type = MessageType.USER
                        )
                        viewModel.dispatchCommand(command)
                    }
                },
                onStop = { viewModel.stopCurrentTask() },
                isTaskRunning = uiState.isTaskRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
}
