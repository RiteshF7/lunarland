package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * Debug-only logs toggle button shown in the corner.
 * Only visible when BuildConfig.DEBUG is true.
 */
@Composable
fun DebugLogsButton(
    showLogs: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only show in debug builds
    val isDebug = try {
        Class.forName("com.termux.BuildConfig")
            .getField("DEBUG")
            .getBoolean(null)
    } catch (e: Exception) {
        false // Default to false if BuildConfig not accessible
    }
    
    if (!isDebug) {
        return // Don't render in release builds
    }
    
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                color = if (showLogs) {
                    LunarTheme.AccentColor.copy(alpha = 0.3f)
                } else {
                    LunarTheme.InactiveBackgroundColor.copy(alpha = 0.6f)
                }
            )
            .clickable(onClick = onToggle)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                id = if (showLogs) {
                    R.drawable.ic_visibility
                } else {
                    R.drawable.ic_visibility_off
                }
            ),
            contentDescription = if (showLogs) "Hide Logs" else "Show Logs",
            tint = if (showLogs) {
                LunarTheme.AccentColor
            } else {
                LunarTheme.TextSecondary
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

