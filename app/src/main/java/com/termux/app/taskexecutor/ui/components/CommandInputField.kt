package com.termux.app.taskexecutor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.termux.app.taskexecutor.model.TaskStatus
import kotlinx.coroutines.CoroutineScope

/**
 * Command Input Field Component
 * Text field with state dot, execute button, and mic button
 */
@Composable
fun CommandInputField(
    commandText: String,
    onCommandTextChange: (String) -> Unit,
    onExecute: () -> Unit,
    taskStatus: TaskStatus,
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    scope: CoroutineScope,
    buttonColor: Color,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    TextField(
        modifier = modifier,
        value = commandText,
        onValueChange = { onCommandTextChange(it) },
        placeholder = {
            Text(text = "Enter Task")
        },
        shape = MaterialTheme.shapes.small,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            imeAction = ImeAction.Done
        ),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        leadingIcon = {
            // State indicator dot
            TaskStateDot(status = taskStatus)
        },
        trailingIcon = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Execute icon
                IconButton(
                    onClick = {
                        if (commandText.isNotBlank()) {
                            onExecute()
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "Execute",
                        tint = buttonColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Mic button
                MicButton(
                    isListening = isListening,
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                    scope = scope,
                    buttonColor = buttonColor,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    )
}

