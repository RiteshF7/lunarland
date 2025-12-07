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

/**
 * Manrope font family matching the HTML design.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

/**
 * Neural Network Active status indicator component.
 * Displays a status card with a green dot and "Neural Network Active" text.
 */
@Composable
fun NeuralNetworkStatus(
    modifier: Modifier = Modifier
) {
    // Use a subtle dark background that matches the theme
    val cardBackgroundColor = Color(0xFF0e1a10).copy(alpha = 0.6f) // Subtle dark background
    val accentColor = Color(0xFF4DFF88)
    
    Box(
        modifier = modifier
            .background(
                color = cardBackgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
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
                        color = accentColor,
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "Neural Network Active",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = manropeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

