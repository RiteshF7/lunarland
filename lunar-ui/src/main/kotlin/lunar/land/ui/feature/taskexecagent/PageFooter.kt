package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Footer component containing action button and status indicator.
 * Wrapped in a glassmorphic green container with dark green border.
 */
@Composable
fun PageFooter(
    aiStatus: String,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Color(0xFF00FF88).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF006644),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                onClick = onSpeakClick,
                modifier = Modifier.fillMaxWidth()
            )

            AIStatusIndicator(
                status = aiStatus,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

