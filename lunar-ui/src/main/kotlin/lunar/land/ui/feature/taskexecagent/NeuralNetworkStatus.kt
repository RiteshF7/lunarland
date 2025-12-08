package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R
import lunar.land.ui.core.theme.LunarTheme

/**
 * Neural Network Active status indicator component.
 * Displays a status card with a green dot and "Neural Network Active" text.
 */
@Composable
fun NeuralNetworkStatus(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = LunarTheme.SecondaryBackgroundColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(LunarTheme.CornerRadius.Large)
            )
            .padding(horizontal = LunarTheme.Spacing.ExtraLarge, vertical = LunarTheme.Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = LunarTheme.AccentColor,
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(LunarTheme.Spacing.Large))
            
            Text(
                text = "Neural Network Active",
                style = LunarTheme.Typography.BodySmall,
                color = LunarTheme.TextPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

