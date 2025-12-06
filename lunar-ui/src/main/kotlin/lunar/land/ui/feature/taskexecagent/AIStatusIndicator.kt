package lunar.land.ui.feature.taskexecagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.R

/**
 * Manrope font family matching the HTML design.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

/**
 * AI status indicator showing the current operational status.
 * Displays a simple green dot with glow effect and status text.
 */
@Composable
fun AIStatusIndicator(
    status: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .drawBehind {
                    // Draw simple glow effect behind the dot
                    val glowRadius = size.minDimension * 1.5f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = Color(0xFF4DFF88).copy(alpha = 0.3f),
                        radius = glowRadius,
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(Color(0xFF4DFF88))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = manropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.White
        )
    }
}

