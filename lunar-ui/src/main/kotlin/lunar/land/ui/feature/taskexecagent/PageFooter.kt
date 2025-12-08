package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * Footer component containing status indicator and tap and hold instruction.
 */
@Composable
fun PageFooter(
    aiStatus: String,
    isTextMode: Boolean = false,
    onTextModeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AIStatusIndicator(
        //     status = aiStatus,
        //     modifier = Modifier.fillMaxWidth()
        // )

        // Description text - changes based on mode
        Text(
            text = if (isTextMode) {
                "Type your command above and press Execute to run it."
            } else {
                "Press and hold the AI sphere to initiate command sequence."
            },
            style = LunarTheme.Typography.BodySmall,
            color = LunarTheme.TextPrimary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = LunarTheme.Spacing.Large)
        )
    }
}

