package lunar.land.ui.feature.lunarcalendar.widget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    height: Dp = 74.dp,
    iconSize: Dp = 40.dp,
    horizontalPadding: Dp = 0.dp,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    textSize: TextUnit? = null,
    supportingTextSize: TextUnit? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .height(height = height)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = horizontalPadding),
        colors = ListItemDefaults.colors(
            containerColor = backgroundColor,
            supportingColor = contentColor,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
            trailingIconColor = contentColor
        ),
        leadingContent = {
            lunarPhaseDetails.getOrNull()?.let {
                LunarPhaseMoonIcon(
                    phaseAngle = it.phaseAngle,
                    illumination = it.illumination,
                    moonSize = iconSize
                )
            }
        },
        headlineContent = {
            lunarPhaseDetails.getOrNull()?.let {
                LunarPhaseName(
                    lunarPhaseDetails = it,
                    showIlluminationPercent = showIlluminationPercent,
                    textColor = contentColor,
                    fontSize = textSize
                )
            }
        },
        supportingContent = if (showUpcomingPhaseDetails) {
            @Composable {
                upcomingLunarPhase.getOrNull()?.let {
                    UpcomingLunarPhaseDetails(
                        upcomingLunarPhase = it,
                        textColor = contentColor, // Use full white color, no transparency
                        fontSize = supportingTextSize ?: textSize // Use supporting text size if provided
                    )
                }
            }
        } else null
    )
}

