package lunar.land.ui.feature.lunarcalendar.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import lunar.land.ui.feature.lunarcalendar.widget.ui.LunarCalendarContent

@Composable
fun LunarCalendarUiComponent(
    state: LunarCalendarUiComponentState,
    modifier: Modifier = Modifier,
    height: Dp = 74.dp,
    iconSize: Dp = 40.dp,
    horizontalPadding: Dp = 0.dp,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    textSize: TextUnit? = null,
    supportingTextSize: TextUnit? = null,
    currentTime: String? = null,
    onClick: (() -> Unit)? = null
) {
    LunarCalendarUiComponentInternal(
        modifier = modifier,
        state = state,
        height = height,
        iconSize = iconSize,
        horizontalPadding = horizontalPadding,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        textSize = textSize,
        supportingTextSize = supportingTextSize,
        currentTime = currentTime,
        onClick = onClick
    )
}

@Composable
private fun LunarCalendarUiComponentInternal(
    state: LunarCalendarUiComponentState,
    modifier: Modifier = Modifier,
    height: Dp = 74.dp,
    iconSize: Dp = 40.dp,
    horizontalPadding: Dp = 0.dp,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    textSize: TextUnit? = null,
    supportingTextSize: TextUnit? = null,
    currentTime: String? = null,
    onClick: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = state.showLunarPhase,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        LunarCalendarContent(
            lunarPhaseDetails = state.lunarPhaseDetails,
            upcomingLunarPhase = state.upcomingLunarPhase,
            showIlluminationPercent = state.showIlluminationPercent,
            showUpcomingPhaseDetails = state.showUpcomingPhaseDetails,
            currentTime = currentTime,
            height = height,
            iconSize = iconSize,
            horizontalPadding = horizontalPadding,
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            textSize = textSize,
            supportingTextSize = supportingTextSize,
            onClick = onClick
        )
    }
}

