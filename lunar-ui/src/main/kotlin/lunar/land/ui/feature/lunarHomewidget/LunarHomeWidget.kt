package lunar.land.ui.feature.lunarHomewidget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.core.model.Constants.Defaults.DEFAULT_CLOCK_24_ANALOG_RADIUS
import lunar.land.ui.core.model.common.getOrNull
import lunar.land.ui.feature.lunarHomewidget.ui.ClockText
import lunar.land.ui.feature.lunarHomewidget.ui.MoonIcon
import lunar.land.ui.feature.lunarHomewidget.ui.MoonInfo
import lunar.land.ui.feature.lunarHomewidget.ui.NextWaning

@Composable
fun LunarHomeWidget(
    state: LunarHomeWidgetState,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    contentColor: Color = Color.White,
    onClick: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    
    // Calculate text size based on clock size (40% smaller than current)
    val clockSizePx = DEFAULT_CLOCK_24_ANALOG_RADIUS * 2
    val spacingPx = with(density) { 4.dp.toPx() }
    val digitHeightPx = clockSizePx * 3 + spacingPx * 2
    val textSize = with(density) { (digitHeightPx * 0.18f).toSp() }
    val supportingTextSize = with(density) { (digitHeightPx * 0.15f).toSp() }
    
    // Calculate moon icon size
    val moonSize = with(density) { (digitHeightPx * 0.9f).toDp() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 32.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Moon icon on the left
        state.lunarPhaseDetails?.getOrNull()?.let { phaseDetails ->
            MoonIcon(
                phaseAngle = phaseDetails.phaseAngle,
                illumination = phaseDetails.illumination,
                moonSize = moonSize,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        // Column on the right with clock, moon info, and next waning
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            // Clock
            ClockText(
                currentTime = state.currentTime,
                textColor = contentColor,
                fontSize = textSize,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Moon info (full moon info)
            state.lunarPhaseDetails?.getOrNull()?.let { phaseDetails ->
                MoonInfo(
                    lunarPhaseDetails = phaseDetails,
                    showIlluminationPercent = state.showIlluminationPercent,
                    textColor = contentColor,
                    fontSize = textSize,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Next waning info
            if (state.showUpcomingPhaseDetails) {
                state.upcomingLunarPhase?.getOrNull()?.let { upcomingPhase ->
                    NextWaning(
                        upcomingLunarPhase = upcomingPhase,
                        textColor = contentColor,
                        fontSize = supportingTextSize
                    )
                }
            }
        }
    }
}

