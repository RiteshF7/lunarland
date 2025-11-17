package lunar.land.ui.core.model

sealed interface PackageAction {
    data class Added(val packageName: String) : PackageAction
    data class Updated(val packageName: String) : PackageAction
    data class Removed(val packageName: String) : PackageAction
}

