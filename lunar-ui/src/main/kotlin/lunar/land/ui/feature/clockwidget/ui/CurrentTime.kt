package lunar.land.ui.feature.clockwidget.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun CurrentTime(
    modifier: Modifier = Modifier,
    currentTime: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
    ) {
        Crossfade(
            label = "Current Time CrossFade",
            targetState = currentTime,
            content = { 
                Text(
                    text = it,
                    color = contentColor,
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        )
    }
}

