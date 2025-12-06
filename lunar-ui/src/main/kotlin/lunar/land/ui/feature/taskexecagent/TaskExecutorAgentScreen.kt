package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Main screen composable for the AI Agent Interaction Sphere.
 * This is the entry point that orchestrates all the UI components.
 */
@Composable
fun TaskExecutorAgentScreen(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    aiStatus: String = "Ready",
    onSpeakClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Section
        PageHeader(
            modifier = Modifier.fillMaxWidth()
        )

        // Main Content - Sphere Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SphereVisualizer(
                modifier = Modifier.size(300.dp),
                isListening = isListening
            )
        }

        // Footer Section
        PageFooter(
            modifier = Modifier.fillMaxWidth(),
            aiStatus = aiStatus,
            onSpeakClick = onSpeakClick
        )
    }
}

