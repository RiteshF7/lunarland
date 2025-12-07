package lunar.land.ui.feature.clockwidget

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.lifecycle.Lifecycle
import lunar.land.ui.core.model.ClockAlignment
import lunar.land.ui.core.model.Constants.Defaults.DEFAULT_CLOCK_24_ANALOG_RADIUS
import lunar.land.ui.core.ui.effects.OnLifecycleEventChange
import lunar.land.ui.core.ui.effects.SystemBroadcastReceiver
import lunar.land.ui.core.ui.extensions.clickableNoRipple
import lunar.land.ui.core.ui.extensions.modifyIf


@Composable
fun ClockWidgetUiComponent(
    state: ClockWidgetUiComponentState,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentColor: Color = Color.White
) {
    // Need to extract the eventSink out to a local val, so that the Compose Compiler
    // treats it as stable. See: https://issuetracker.google.com/issues/256100927
    val eventSink = state.eventSink

    ClockWidgetUiComponent(
        modifier = modifier,
        state = state,
        refreshTime = { eventSink(ClockWidgetUiComponentUiEvent.RefreshTime) },
        horizontalPadding = horizontalPadding,
        onClick = onClick,
        contentColor = contentColor
    )
}

@Composable
private fun ClockWidgetUiComponent(
    state: ClockWidgetUiComponentState,
    refreshTime: () -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentColor: Color = Color.White
) {
    val updatedRefreshTime by rememberUpdatedState(newValue = refreshTime)

    val horizontalBias by animateFloatAsState(
        label = "Clock Alignment",
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        targetValue = when (state.clockAlignment) {
            ClockAlignment.START -> -1f
            ClockAlignment.CENTER -> 0f
            ClockAlignment.END -> 1f
        }
    )

    SystemBroadcastReceiver(systemAction = Intent.ACTION_TIME_TICK) {
        updatedRefreshTime()
    }

    OnLifecycleEventChange { event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            updatedRefreshTime()
        }
    }

    val clickModifier = Modifier.modifyIf(predicate = { onClick != null }) {
        clickableNoRipple { onClick?.invoke() }
    }

    Log.d("ClockWidget", "Rendering clock with currentTime: '${state.currentTime}'")
    
    // Calculate text size to match lunar calendar (same calculation)
    val density = LocalDensity.current
    val clockSizePx = DEFAULT_CLOCK_24_ANALOG_RADIUS * 2
    val spacingPx = with(density) { 4.dp.toPx() }
    val digitHeightPx = clockSizePx * 3 + spacingPx * 2
    val textSize = with(density) { (digitHeightPx * 0.5f).toSp() } // Same as lunar calendar text size
    
    Column(
        horizontalAlignment = BiasAlignment.Horizontal(bias = horizontalBias),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        Text(
            text = state.currentTime,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = textSize,
                color = contentColor
            ),
            modifier = clickModifier
        )
    }
}

