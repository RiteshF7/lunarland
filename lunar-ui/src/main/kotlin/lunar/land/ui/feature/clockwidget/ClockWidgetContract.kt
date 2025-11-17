package lunar.land.ui.feature.clockwidget

import lunar.land.ui.core.model.ClockAlignment

data class ClockWidgetUiComponentState(
    val currentTime: String,
    val showClock24: Boolean,
    val use24Hour: Boolean,
    val clockAlignment: ClockAlignment,
    val clock24AnimationDuration: Int,
    val eventSink: (ClockWidgetUiComponentUiEvent) -> Unit
)

sealed interface ClockWidgetUiComponentUiEvent {
    object RefreshTime : ClockWidgetUiComponentUiEvent
}

