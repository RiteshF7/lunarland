package lunar.land.ui.feature.lunarHomewidget.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

@Composable
internal fun ClockText(
    modifier: Modifier = Modifier,
    currentTime: String,
    textColor: Color,
    fontSize: TextUnit
) {
    Text(
        text = currentTime,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = fontSize,
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
        ),
        modifier = modifier
    )
}

