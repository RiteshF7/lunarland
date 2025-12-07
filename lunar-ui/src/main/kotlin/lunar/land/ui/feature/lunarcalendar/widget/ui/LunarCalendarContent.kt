package lunar.land.ui.feature.lunarcalendar.widget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.core.model.common.State
import lunar.land.ui.core.model.common.getOrNull
import lunar.land.ui.core.model.lunarphase.LunarPhaseDetails
import lunar.land.ui.core.model.lunarphase.UpcomingLunarPhase
import lunar.land.ui.feature.lunarcalendar.shared.LunarPhaseMoonIcon

@Composable
internal fun LunarCalendarContent(
    lunarPhaseDetails: State<LunarPhaseDetails>,
    upcomingLunarPhase: State<UpcomingLunarPhase>,
    showIlluminationPercent: Boolean,
    showUpcomingPhaseDetails: Boolean,
    currentTime: String? = null,
    height: Dp = 74.dp,
    iconSize: Dp = 40.dp,
    horizontalPadding: Dp = 0.dp,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    textSize: TextUnit? = null,
    supportingTextSize: TextUnit? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .height(height = height)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Moon icon
        lunarPhaseDetails.getOrNull()?.let {
            LunarPhaseMoonIcon(
                phaseAngle = it.phaseAngle,
                illumination = it.illumination,
                moonSize = iconSize
            )
        }
        
        // Clock (time)
        if (currentTime != null && textSize != null) {
            Text(
                text = currentTime,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = textSize,
                    color = Color.White
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        // Full Moon info
        lunarPhaseDetails.getOrNull()?.let {
            LunarPhaseName(
                lunarPhaseDetails = it,
                showIlluminationPercent = showIlluminationPercent,
                textColor = contentColor,
                fontSize = textSize
            )
        }
        
        // Next waning info
        if (showUpcomingPhaseDetails) {
            upcomingLunarPhase.getOrNull()?.let {
                UpcomingLunarPhaseDetails(
                    upcomingLunarPhase = it,
                    textColor = contentColor,
                    fontSize = supportingTextSize ?: textSize
                )
            }
        }
    }
}

