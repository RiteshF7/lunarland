package lunar.land.ui.feature.taskexecagent.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.termux.app.taskexecutor.model.TaskExecutorMode
import lunar.land.ui.R

/**
 * Mode Toggle Button Component
 * Button to switch between TEXT and VOICE modes
 */
@Composable
fun ModeToggleButton(
    currentMode: TaskExecutorMode,
    onModeChange: (TaskExecutorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

