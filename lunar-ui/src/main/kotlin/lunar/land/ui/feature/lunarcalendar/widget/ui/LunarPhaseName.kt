package lunar.land.ui.feature.lunarcalendar.widget.ui

import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import lunar.land.ui.core.model.common.asPercent
import lunar.land.ui.core.model.lunarphase.LunarPhaseDetails
import lunar.land.ui.core.ui.extensions.string

@Composable
internal fun LunarPhaseName(
    modifier: Modifier = Modifier,
    lunarPhaseDetails: LunarPhaseDetails,
    showIlluminationPercent: Boolean,
    textColor: Color,
    fontSize: TextUnit? = null
) {
    val phaseNameAndIlluminationPercentPair = lunarPhaseDetails.run {
        lunarPhase.phaseNameUiText.string() to (illumination * 100).asPercent()
    }
    val text = phaseNameAndIlluminationPercentPair.let {
        it.first + if (showIlluminationPercent) " (${it.second})" else ""
    }
    Crossfade(
        modifier = modifier,
        label = "Cross Fade Lunar Phase name",
        targetState = text
    ) {
        Text(
            text = it,
            color = Color.White, // Force white color
            style = if (fontSize != null) {
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = fontSize,
                    color = Color.White
                )
            } else {
                MaterialTheme.typography.titleMedium.copy(color = Color.White)
            }
        )
    }
}

