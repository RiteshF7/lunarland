package lunar.land.ui.core.model

import lunar.land.ui.R

enum class Theme(val uiText: UiText) {
    FOLLOW_SYSTEM(uiText = UiText.Resource(stringRes = R.string.follow_system)),
    NOT_WHITE(uiText = UiText.Resource(stringRes = R.string.not_white)),
    SAID_DARK(uiText = UiText.Resource(stringRes = R.string.said_dark))
}

