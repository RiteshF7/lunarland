package com.termux.app.taskexecutor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.termux.app.taskexecutor.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import lunar.land.ui.R

/**
 * Voice Only UI Component
 * Shows mic button with real-time transcription, task status, and stop button
 */
@Composable
fun VoiceOnlyUI(
    isListening: Boolean,
    transcriptionText: String,
    taskStatus: TaskStatus,
    statusText: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopTask: () -> Unit,
    scope: CoroutineScope,
    buttonColor: Color,
    modifier: Modifier = Modifier
) {
    val isTaskRunning = taskStatus == TaskStatus.RUNNING
    
    // Multiple pulsing circles animation when listening (WhatsApp style)
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    
    // First expanding circle
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 2.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.4f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_alpha1"
    )
    
    // Second expanding circle (delayed)
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 2.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_scale2"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.4f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_alpha2"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Task status dot and status text row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskStateDot(status = taskStatus)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText.ifEmpty { 
                    when (taskStatus) {
                        TaskStatus.STOPPED -> "Ready"
                        TaskStatus.RUNNING -> "Running..."
                        TaskStatus.SUCCESS -> "Success"
                        TaskStatus.ERROR -> "Error"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        
        // Stop button (only show when task is running)
        if (isTaskRunning) {
            Button(
                onClick = onStopTask,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.6f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Stop Task",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Task")
            }
        }
        
        // Real-time transcription text
        if (transcriptionText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = buttonColor.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = transcriptionText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Instruction text
        Text(
            text = if (isListening) "Listening... Release to stop" 
                  else if (isTaskRunning) "Task is running. Press stop to cancel."
                  else "Press and hold to speak",
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                isListening -> MaterialTheme.colorScheme.error
                isTaskRunning -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Mic button with WhatsApp-style animation (disabled when task is running)
        Box(
            modifier = Modifier
                .size(80.dp)
                .pointerInput(isTaskRunning) {
                    if (!isTaskRunning) {
                        detectTapGestures(
                            onPress = { _ ->
                                scope.launch {
                                    // Start voice input when pressed
                                    onStartListening()
                                    // Wait for release
                                    tryAwaitRelease()
                                    // Stop voice input when released
                                    onStopListening()
                                }
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Expanding pulsing circles (WhatsApp style)
            if (isListening) {
                // First expanding circle
                Box(
                    modifier = Modifier
                        .size(80.dp * scale1)
                        .clip(shape = CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = alpha1)
                        )
                        .align(Alignment.Center)
                )
                
                // Second expanding circle (delayed)
                Box(
                    modifier = Modifier
                        .size(80.dp * scale2)
                        .clip(shape = CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = alpha2)
                        )
                        .align(Alignment.Center)
                )
            }
            
            // Main button background
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(shape = CircleShape)
                    .background(
                        color = when {
                            isListening -> MaterialTheme.colorScheme.error
                            isTaskRunning -> buttonColor.copy(alpha = 0.3f)
                            else -> buttonColor.copy(alpha = 0.1f)
                        }
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mic),
                    contentDescription = when {
                        isListening -> "Release to stop listening"
                        isTaskRunning -> "Task is running"
                        else -> "Press and hold to speak"
                    },
                    tint = when {
                        isListening -> Color.White
                        isTaskRunning -> buttonColor.copy(alpha = 0.5f)
                        else -> buttonColor
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}
