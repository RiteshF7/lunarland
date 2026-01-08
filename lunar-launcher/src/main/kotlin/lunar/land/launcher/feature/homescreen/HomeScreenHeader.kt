package lunar.land.launcher.feature.homescreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Header component for HomeScreen with title and subtitle.
 */
@Composable
fun HomeScreenHeader(
    modifier: Modifier = Modifier,
    title: String = "Lunar Land",
    subtitle: String = "Your digital home in the void."
) {
    Column(
        modifier = modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = HomeScreenTheme.manropeFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = HomeScreenTheme.manropeFontFamily
            ),
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

