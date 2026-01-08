package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text input panel component matching the chatbot UI design.
 * Features a light grey input field with plus icon on left and send icon on right.
 */
@Composable
fun TextInputPanel(
    onExecute: (String) -> Unit,
    onStop: (() -> Unit)? = null,
    isTaskRunning: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Note: onFocusChange is kept for API compatibility but not currently used
    var text by remember { mutableStateOf("") }
    
    // Light grey background matching the image
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF3A3A3A))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Plus icon button on the left (circular white)
        IconButton(
            onClick = { /* Handle attachment/add action */ },
            modifier = Modifier.size(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = Color(0xFF3A3A3A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Text field in the middle
        TextField(
            value = text,
            onValueChange = { if (!isTaskRunning) text = it },
            enabled = !isTaskRunning,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            placeholder = {
                Text(
                    text = if (isTaskRunning) "Task running..." else "Send a message...",
                    color = Color(0xFF999999),
                    fontSize = 14.sp
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = Color.White
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (text.isNotBlank() && !isTaskRunning) {
                        onExecute(text.trim())
                        text = ""
                    }
                }
            ),
            shape = RoundedCornerShape(0.dp)
        )
        
        // Send/Stop icon button on the right
        IconButton(
            onClick = {
                if (isTaskRunning && onStop != null) {
                    onStop()
                } else if (text.isNotBlank()) {
                    onExecute(text.trim())
                    text = ""
                }
            },
            modifier = Modifier.size(40.dp),
            enabled = text.isNotBlank() || (isTaskRunning && onStop != null)
        ) {
            Icon(
                imageVector = if (isTaskRunning && onStop != null) {
                    Icons.Default.Stop
                } else {
                    Icons.AutoMirrored.Filled.Send
                },
                contentDescription = if (isTaskRunning && onStop != null) "Stop" else "Send",
                tint = if (text.isNotBlank() || (isTaskRunning && onStop != null)) {
                    Color.White
                } else {
                    Color(0xFF666666)
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

