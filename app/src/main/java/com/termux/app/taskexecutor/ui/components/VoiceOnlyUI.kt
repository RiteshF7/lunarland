package com.termux.app.taskexecutor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import lunar.land.ui.R

/**
 * Voice Only UI Component
 * Shows only the mic button with instructions for voice input
 */
@Composable
fun VoiceOnlyUI(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    scope: CoroutineScope,
    buttonColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
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
        // Instruction text
        Text(
            text = if (isListening) "Listening... Release to stop" else "Press and hold to speak",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Mic button with WhatsApp-style animation
        Box(
            modifier = Modifier
                .size(80.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
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
                        color = if (isListening) 
                            MaterialTheme.colorScheme.error 
                        else 
                            buttonColor.copy(alpha = 0.1f)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { /* Handled by pointerInput */ }
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mic),
                    contentDescription = if (isListening) "Release to stop listening" else "Press and hold to speak",
                    tint = if (isListening) Color.White else buttonColor,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

