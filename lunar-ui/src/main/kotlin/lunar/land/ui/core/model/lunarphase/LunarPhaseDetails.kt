package lunar.land.ui.core.model.lunarphase

import androidx.compose.runtime.Immutable

@Immutable
data class LunarPhaseDetails(
    val lunarPhase: LunarPhase,
    val illumination: Double,
    val phaseAngle: Double,
    val nextPhaseDetails: NextPhaseDetails,
    val moonRiseAndSetDetails: RiseAndSetDetails,
    val sunRiseAndSetDetails: RiseAndSetDetails
) {
    val direction: LunarPhaseDirection
        get() = when (lunarPhase) {
            LunarPhase.NEW_MOON, LunarPhase.WAXING_CRESCENT, LunarPhase.FIRST_QUARTER, LunarPhase.WAXING_GIBBOUS -> 
                LunarPhaseDirection.NEW_TO_FULL
            LunarPhase.FULL_MOON, LunarPhase.WANING_GIBBOUS, LunarPhase.LAST_QUARTER, LunarPhase.WANING_CRESCENT -> 
                LunarPhaseDirection.FULL_TO_NEW
        }
}

