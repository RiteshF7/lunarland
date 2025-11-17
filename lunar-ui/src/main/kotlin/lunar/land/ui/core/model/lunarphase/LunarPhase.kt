package lunar.land.ui.core.model.lunarphase

import lunar.land.ui.core.model.UiText
import lunar.land.ui.R

enum class LunarPhase(val phaseNameUiText: UiText) {
    NEW_MOON(phaseNameUiText = UiText.Resource(stringRes = R.string.new_moon)),
    WAXING_CRESCENT(phaseNameUiText = UiText.Resource(stringRes = R.string.waxing_crescent)),
    FIRST_QUARTER(phaseNameUiText = UiText.Resource(stringRes = R.string.first_quarter)),
    WAXING_GIBBOUS(phaseNameUiText = UiText.Resource(stringRes = R.string.waxing_gibbous)),
    FULL_MOON(phaseNameUiText = UiText.Resource(stringRes = R.string.full_moon)),
    WANING_GIBBOUS(phaseNameUiText = UiText.Resource(stringRes = R.string.waning_gibbous)),
    LAST_QUARTER(phaseNameUiText = UiText.Resource(stringRes = R.string.last_quarter)),
    WANING_CRESCENT(phaseNameUiText = UiText.Resource(stringRes = R.string.waning_crescent));
    
    val phaseName: String
        get() = when (this) {
            NEW_MOON -> "New Moon"
            WAXING_CRESCENT -> "Waxing Crescent"
            FIRST_QUARTER -> "First Quarter"
            WAXING_GIBBOUS -> "Waxing Gibbous"
            FULL_MOON -> "Full Moon"
            WANING_GIBBOUS -> "Waning Gibbous"
            LAST_QUARTER -> "Last Quarter"
            WANING_CRESCENT -> "Waning Crescent"
        }
}

