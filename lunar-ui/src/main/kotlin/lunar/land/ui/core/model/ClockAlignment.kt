package lunar.land.ui.core.model

enum class ClockAlignment(
    val index: Int,
    val uiText: UiText
) {
    START(
        index = 0,
        uiText = UiText.Resource(stringRes = lunar.land.ui.R.string.start)
    ),
    CENTER(
        index = 1,
        uiText = UiText.Resource(stringRes = lunar.land.ui.R.string.center)
    ),
    END(
        index = 2,
        uiText = UiText.Resource(stringRes = lunar.land.ui.R.string.end)
    )
}

