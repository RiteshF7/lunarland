package lunar.land.ui.core.homescreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lunar.land.ui.core.homescreen.model.LocalHomePadding
import lunar.land.ui.core.model.Constants.Defaults.DEFAULT_CLOCK_24_ANALOG_RADIUS
import lunar.land.ui.feature.lunarcalendar.widget.LunarCalendarUiComponent
import lunar.land.ui.feature.lunarcalendar.widget.LunarCalendarUiComponentState

@Composable
internal fun DecoratedLunarCalendar(
    state: LunarCalendarUiComponentState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    horizontalPadding: Dp,
    currentTime: String? = null,
    contentColor: Color = Color.White
) {
    val density = LocalDensity.current
    // Calculate size based on clock: clock radius is 30f, so clock size is 60px
    // Digit height is approximately 3 clocks + 2 spacings (4dp each)
    val clockSizePx = DEFAULT_CLOCK_24_ANALOG_RADIUS * 2
    val spacingPx = with(density) { 4.dp.toPx() }
    val digitHeightPx = clockSizePx * 3 + spacingPx * 2
    
    // Scale icon and height proportionally to clock size - increased sizes
    val iconSize = with(density) { (digitHeightPx * 0.9f).toDp() } // Icon is 90% of digit height
    val height = with(density) { (digitHeightPx * 1.2f).toDp() } // Height is 120% of digit height
    val textSize = with(density) { (digitHeightPx * 0.5f).toSp() } // Text is 50% of digit height
    val supportingTextSize = with(density) { (digitHeightPx * 0.4f).toSp() } // Supporting text is 40% of digit height

    Box(modifier = modifier) {
        LunarCalendarUiComponent(
            state = state,
            height = height,
            iconSize = iconSize,
            horizontalPadding = horizontalPadding,
            contentColor = contentColor,
            textSize = textSize,
            supportingTextSize = supportingTextSize,
            currentTime = currentTime,
            onClick = onClick
        )
    }
}

