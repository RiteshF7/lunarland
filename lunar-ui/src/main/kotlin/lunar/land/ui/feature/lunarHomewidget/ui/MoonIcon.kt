package lunar.land.ui.feature.lunarHomewidget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import lunar.land.ui.feature.lunarcalendar.shared.LunarPhaseMoonIcon

@Composable
internal fun MoonIcon(
    modifier: Modifier = Modifier,
    phaseAngle: Double,
    illumination: Double,
    moonSize: Dp
) {
    LunarPhaseMoonIcon(
        modifier = modifier,
        phaseAngle = phaseAngle,
        illumination = illumination,
        moonSize = moonSize
    )
}

