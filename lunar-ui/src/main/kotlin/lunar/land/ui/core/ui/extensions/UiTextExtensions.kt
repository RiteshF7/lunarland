package lunar.land.ui.core.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lunar.land.ui.core.model.UiText

@Composable
fun UiText.string(): String = when (this) {
    is UiText.Static -> text
    is UiText.Resource -> stringResource(id = stringRes)
}

