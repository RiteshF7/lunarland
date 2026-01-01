package lunar.land.ui.feature.taskexecagent.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.termux.app.taskexecutor.model.TaskStatus

/**
 * Task State Dot Component
 * Shows a dot that indicates task status by color:
 * - Light green = idle (STOPPED)
 * - Red = error (ERROR)
 * - Blinking green = running (RUNNING)
 * - Green = success (SUCCESS)
 */
@Composable
fun TaskStateDot(
    status: TaskStatus,
    modifier: Modifier = Modifier
) {
    val dotColor = when (status) {
        TaskStatus.STOPPED -> Color(0xFF90EE90) // Light green
        TaskStatus.ERROR -> Color.Red
        TaskStatus.RUNNING -> Color.Green
        TaskStatus.SUCCESS -> Color.Green
    }
    
    // Blinking animation for running state
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink_alpha"
    )
    
    val animatedColor = if (status == TaskStatus.RUNNING) {
        dotColor.copy(alpha = alpha)
    } else {
        dotColor
    }
    
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(shape = CircleShape)
            .background(color = animatedColor)
    )
}

