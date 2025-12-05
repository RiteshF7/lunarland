package com.termux.app.taskexecutor.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.termux.app.taskexecutor.model.TaskExecutorMode
import com.termux.app.taskexecutor.model.TaskStatus
import lunar.land.ui.R

/**
 * Command Input Field Component
 * Text field with state dot and mode toggle button
 */
@Composable
fun CommandInputField(
    commandText: String,
    onCommandTextChange: (String) -> Unit,
    onExecute: () -> Unit,
    taskStatus: TaskStatus,
    currentMode: TaskExecutorMode,
    onModeChange: (TaskExecutorMode) -> Unit,
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
        keyboardActions = KeyboardActions(
            onDone = {
                if (commandText.isNotBlank()) {
                    onExecute()
                    keyboardController?.hide()
                }
            }
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
            // Mode toggle button
            IconButton(
                onClick = {
                    onModeChange(
                        if (currentMode == TaskExecutorMode.TEXT) 
                            TaskExecutorMode.VOICE 
                        else 
                            TaskExecutorMode.TEXT
                    )
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = if (currentMode == TaskExecutorMode.TEXT) 
                        painterResource(id = R.drawable.ic_mic)
                    else 
                        painterResource(id = R.drawable.ic_close),
                    contentDescription = if (currentMode == TaskExecutorMode.TEXT) 
                        "Switch to Voice Mode" 
                    else 
                        "Switch to Text Mode",
                    tint = buttonColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    )
}

