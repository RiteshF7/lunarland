package lunar.land.ui.feature.lunarcalendar.widget.ui

import androidx.compose.animation.Crossfade
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import lunar.land.ui.core.model.common.inShortReadableFormat
import lunar.land.ui.core.model.lunarphase.UpcomingLunarPhase
import lunar.land.ui.R
import lunar.land.ui.core.ui.extensions.string

@Composable
internal fun UpcomingLunarPhaseDetails(
    modifier: Modifier = Modifier,
    upcomingLunarPhase: UpcomingLunarPhase,
    textColor: Color
) {
    val phaseName = upcomingLunarPhase.lunarPhase.phaseNameUiText.string()
    val dateTime = upcomingLunarPhase.dateTime?.inShortReadableFormat() ?: return
    val nextPhaseOnText = stringResource(id = R.string.next_phase_is_on_date, phaseName, dateTime)

    Crossfade(
        modifier = modifier,
        label = "Cross Fade Upcoming Lunar Phase Details",
        targetState = nextPhaseOnText
    ) {
        Text(
            text = it,
            color = textColor
        )
    }
}

