package lunar.land.ui.feature.clockwidget

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import lunar.land.ui.core.model.ClockAlignment
import lunar.land.ui.core.ui.effects.OnLifecycleEventChange
import lunar.land.ui.core.ui.effects.SystemBroadcastReceiver
import lunar.land.ui.core.ui.extensions.clickableNoRipple
import lunar.land.ui.core.ui.extensions.modifyIf
import lunar.land.ui.feature.clockwidget.ui.Clock24


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

    Column(
        horizontalAlignment = BiasAlignment.Horizontal(bias = horizontalBias),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        Clock24(
            modifier = clickModifier,
            currentTime = state.currentTime,
            handleColor = contentColor,
            offsetAnimationSpec = tween(durationMillis = state.clock24AnimationDuration),
            colorAnimationSpec = tween(durationMillis = state.clock24AnimationDuration)
        )
    }
}

