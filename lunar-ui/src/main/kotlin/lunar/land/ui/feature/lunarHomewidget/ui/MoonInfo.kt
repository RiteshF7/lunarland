package lunar.land.ui.feature.lunarHomewidget.ui

import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import lunar.land.ui.core.model.common.asPercent
import lunar.land.ui.core.model.lunarphase.LunarPhaseDetails
import lunar.land.ui.core.ui.extensions.string

@Composable
internal fun MoonInfo(
    modifier: Modifier = Modifier,
    lunarPhaseDetails: LunarPhaseDetails,
    showIlluminationPercent: Boolean,
    textColor: Color,
    fontSize: TextUnit
) {
    val phaseName = lunarPhaseDetails.lunarPhase.phaseNameUiText.string()
    val illuminationPercent = (lunarPhaseDetails.illumination * 100).asPercent()
    val text = if (showIlluminationPercent) {
        "$phaseName ($illuminationPercent)"
    } else {
        phaseName
    }

    Crossfade(
        modifier = modifier,
        label = "Cross Fade Lunar Phase name",
        targetState = text
    ) {
        Text(
            text = it,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = fontSize,
                color = Color.White
            )
        )
    }
}

