package lunar.land.ui.feature.favorites

import kotlinx.collections.immutable.ImmutableList
import lunar.land.ui.core.model.app.AppWithColor

data class FavoritesListUiComponentState(
    val favoritesList: ImmutableList<AppWithColor>,
    val eventSink: (FavoritesListUiComponentUiEvent) -> Unit
)

sealed interface FavoritesListUiComponentUiEvent {
    data object AddDefaultAppsIfRequired : FavoritesListUiComponentUiEvent
}

