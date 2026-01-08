package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    
    // Toggle for showing all logs vs filtered logs
    var showAllLogs by remember { mutableStateOf(false) }
    
    // Chat message state
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var lastOutputText by remember { mutableStateOf("") }
    var lastStatusText by remember { mutableStateOf("") }
    var lastCurrentTask by remember { mutableStateOf<String?>(null) }
    
    // Filter messages based on toggle state
    val filteredMessages = remember(chatMessages, showAllLogs) {
        if (showAllLogs) {
            chatMessages
        } else {
            chatMessages.filter { message ->
                LogFilter.shouldShowMessage(message, showAllLogs)
            }
        }
    }
    
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
                    // Try to extract user-friendly messages from output
                    val friendlyMessages = LogFilter.extractUserFriendlyMessages(trimmedOutput)
                    
                    if (friendlyMessages.isNotEmpty()) {
                        // Add each friendly message as a separate system message
                        friendlyMessages.forEach { friendlyMsg ->
                            chatMessages = chatMessages + ChatMessage(
                                text = friendlyMsg,
                                type = MessageType.SYSTEM
                            )
                        }
                    } else {
                        // If no friendly messages found, add as output (will be filtered if not friendly)
                        chatMessages = chatMessages + ChatMessage(
                            text = trimmedOutput,
                            type = MessageType.OUTPUT
                        )
                    }
                }
            }
            lastOutputText = uiState.outputText
        }
    }
    
    // Track task status changes and cleanup
    LaunchedEffect(uiState.taskStatus, uiState.isTaskRunning) {
        when (uiState.taskStatus) {
            TaskStatus.SUCCESS -> {
                if (!uiState.isTaskRunning) {
                    // Task completed - add success message
                    chatMessages = chatMessages + ChatMessage(
                        text = "✓ Task completed successfully",
                        type = MessageType.SYSTEM
                    )
                }
            }
            TaskStatus.ERROR -> {
                if (!uiState.isTaskRunning) {
                    // Task failed - add error message
                    chatMessages = chatMessages + ChatMessage(
                        text = "✗ Task failed or error occurred",
                        type = MessageType.ERROR
                    )
                }
            }
            TaskStatus.STOPPED -> {
                if (!uiState.isTaskRunning) {
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
            // Toggle button at the top
            if (chatMessages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showAllLogs = !showAllLogs },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            imageVector = if (showAllLogs) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (showAllLogs) "Hide technical logs" else "Show all logs",
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showAllLogs) "Hide technical logs" else "Show all logs",
                            color = Color(0xFF999999),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            // Chat messages area
            if (filteredMessages.isNotEmpty()) {
                ChatMessageList(
                    messages = filteredMessages,
                    isTaskRunning = uiState.isTaskRunning,
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
