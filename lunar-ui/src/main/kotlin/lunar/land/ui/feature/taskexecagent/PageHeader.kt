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

/**
 * Manrope font family matching the HTML design.
 */
private val manropeFontFamily = FontFamily(
    Font(resId = R.font.manrope_variable, weight = FontWeight.Normal)
)

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
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = manropeFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White,
            textAlign = TextAlign.Start
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Agent Loon is a agent in Lunar Land.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = manropeFontFamily
            ),
            color = Color.White,
            textAlign = TextAlign.Start
        )
    }
}

