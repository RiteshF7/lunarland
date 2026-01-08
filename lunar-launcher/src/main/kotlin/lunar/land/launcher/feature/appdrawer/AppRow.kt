package lunar.land.launcher.feature.appdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A row container for app items with consistent spacing.
 * 
 * @param modifier Modifier for styling and layout
 * @param isCentered Whether to center the items in the row
 * @param content The app items to display in the row
 */
@Composable
fun AppRow(
    modifier: Modifier = Modifier,
    isCentered: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isCentered) 
            Arrangement.Center 
        else 
            Arrangement.spacedBy(20.dp),
    ) {
        content()
    }
}
