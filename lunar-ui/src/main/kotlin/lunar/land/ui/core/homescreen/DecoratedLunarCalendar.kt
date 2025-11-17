package lunar.land.ui.core.homescreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import lunar.land.ui.core.homescreen.model.LocalHomePadding
import lunar.land.ui.feature.lunarcalendar.widget.LunarCalendarUiComponent
import lunar.land.ui.feature.lunarcalendar.widget.LunarCalendarUiComponentState

@Composable
internal fun DecoratedLunarCalendar(
    state: LunarCalendarUiComponentState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    val homePadding = LocalHomePadding.current
    val extraLunarPhaseIconSize = 1.dp
    val iconSize = homePadding.lunarPhaseIconSize + extraLunarPhaseIconSize

    Box(modifier = modifier) {
        LunarCalendarUiComponent(
            state = state,
            iconSize = iconSize,
            horizontalPadding = horizontalPadding,
            onClick = onClick
        )
    }
}

