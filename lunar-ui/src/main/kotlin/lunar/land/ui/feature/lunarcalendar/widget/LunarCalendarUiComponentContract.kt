package lunar.land.ui.feature.lunarcalendar.widget

import lunar.land.ui.core.model.common.State
import lunar.land.ui.core.model.lunarphase.LunarPhaseDetails
import lunar.land.ui.core.model.lunarphase.UpcomingLunarPhase

data class LunarCalendarUiComponentState(
    val showLunarPhase: Boolean,
    val showIlluminationPercent: Boolean,
    val showUpcomingPhaseDetails: Boolean,
    val lunarPhaseDetails: State<LunarPhaseDetails>,
    val upcomingLunarPhase: State<UpcomingLunarPhase>
)

