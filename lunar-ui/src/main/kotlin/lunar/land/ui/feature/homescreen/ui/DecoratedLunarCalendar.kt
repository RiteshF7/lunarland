package lunar.land.ui.feature.homescreen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lunar.land.ui.core.homescreen.model.LocalHomePadding
import lunar.land.ui.feature.lunarcalendar.widget.LunarCalendarUiComponent
import lunar.land.ui.feature.lunarcalendar.widget.LunarCalendarUiComponentState
import lunar.land.ui.feature.lunarcalendar.widget.ui.LunarPhaseName
import lunar.land.ui.feature.lunarcalendar.widget.ui.UpcomingLunarPhaseDetails
import lunar.land.ui.feature.lunarcalendar.shared.LunarPhaseMoonIcon
import lunar.land.ui.core.model.common.getOrNull

/**
 * Decorated wrapper for LunarCalendarUiComponent with home screen specific styling.
 * Now includes clock integrated vertically with moon and lunar phase info.
 */
@Composable
fun DecoratedLunarCalendar(
    state: LunarCalendarUiComponentState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    horizontalPadding: Dp,
    currentTime: String? = null,
    contentColor: Color = Color.White
) {
    val homePadding = LocalHomePadding.current
    val extraLunarPhaseIconSize = 1.dp
    val iconSize = homePadding.lunarPhaseIconSize + extraLunarPhaseIconSize
    
    // Use lunar calendar's natural typography sizes (no clock-based calculation)
    // Clock and lunar text will use default MaterialTheme typography sizes:
    // - titleMedium (16.sp) for main text (clock and phase name)
    // - bodyMedium (14.sp) for supporting text (upcoming phase)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .padding(top = 8.dp)
                .clickable { onClick() },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Moon icon - first horizontally
            state.lunarPhaseDetails.getOrNull()?.let {
                LunarPhaseMoonIcon(
                    phaseAngle = it.phaseAngle,
                    illumination = it.illumination,
                    moonSize = iconSize
                )
            }
            
            // All text content in a column after the moon
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Clock (time) - use same font and style as lunar calendar
                if (currentTime != null) {
                    Text(
                        text = currentTime,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White
                        ),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                
                // Full Moon info - use default typography size
                state.lunarPhaseDetails.getOrNull()?.let {
                    LunarPhaseName(
                        lunarPhaseDetails = it,
                        showIlluminationPercent = state.showIlluminationPercent,
                        textColor = contentColor,
                        fontSize = null // Use default typography size
                    )
                }
                
                // Next waning info - use default typography size
                if (state.showUpcomingPhaseDetails) {
                    state.upcomingLunarPhase.getOrNull()?.let {
                        UpcomingLunarPhaseDetails(
                            upcomingLunarPhase = it,
                            textColor = contentColor,
                            fontSize = null // Use default typography size
                        )
                    }
                }
            }
        }
    }
}

