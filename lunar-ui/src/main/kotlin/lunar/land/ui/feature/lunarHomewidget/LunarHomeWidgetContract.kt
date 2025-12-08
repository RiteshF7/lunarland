package lunar.land.ui.feature.lunarHomewidget

import lunar.land.ui.core.model.common.State
import lunar.land.ui.core.model.lunarphase.LunarPhaseDetails
import lunar.land.ui.core.model.lunarphase.UpcomingLunarPhase

data class LunarHomeWidgetState(
    val currentTime: String,
    val lunarPhaseDetails: State<LunarPhaseDetails>?,
    val upcomingLunarPhase: State<UpcomingLunarPhase>?,
    val showIlluminationPercent: Boolean,
    val showUpcomingPhaseDetails: Boolean
)

