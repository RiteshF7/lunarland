package lunar.land.ui.feature.lunarHomewidget.ui

import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import lunar.land.ui.core.model.common.inShortReadableFormat
import lunar.land.ui.core.model.lunarphase.UpcomingLunarPhase
import lunar.land.ui.R
import lunar.land.ui.core.ui.extensions.string

@Composable
internal fun NextWaning(
    modifier: Modifier = Modifier,
    upcomingLunarPhase: UpcomingLunarPhase,
    textColor: Color,
    fontSize: TextUnit
) {
    val phaseName = upcomingLunarPhase.lunarPhase.phaseNameUiText.string()
    val dateTime = upcomingLunarPhase.dateTime?.inShortReadableFormat() ?: return
    
    val text = stringResource(
        id = R.string.next_phase_is_on_date,
        phaseName,
        dateTime
    )

    Crossfade(
        modifier = modifier,
        label = "Cross Fade Upcoming Lunar Phase Details",
        targetState = text
    ) {
        Text(
            text = it,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = fontSize,
                color = Color.White
            )
        )
    }
}

