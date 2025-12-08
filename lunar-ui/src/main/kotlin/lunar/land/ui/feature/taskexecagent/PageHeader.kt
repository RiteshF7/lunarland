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
 * Header component displaying the title and instructions.
 */
@Composable
fun PageHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Agent Loon ",
            style = LunarTheme.Typography.DisplayLarge.copy(
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                letterSpacing = (-0.5).sp
            ),
            color = LunarTheme.TextPrimary,
            textAlign = TextAlign.Start
        )
        
        Spacer(modifier = Modifier.height(LunarTheme.Spacing.Small))
        
        Text(
            text = "Navigator of Lunar Land. Your voice in the void.",
            style = LunarTheme.Typography.BodyMedium,
            color = LunarTheme.TextPrimary,
            textAlign = TextAlign.Start
        )
    }
}

