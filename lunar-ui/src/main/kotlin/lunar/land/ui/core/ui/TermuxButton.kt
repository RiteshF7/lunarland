package lunar.land.ui.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import lunar.land.ui.core.theme.LunarTheme

/**
 * Specialized button component for launching Termux.
 * Reusable across the app with consistent styling.
 */
@Composable
fun TermuxButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    NavigationButton(
        text = "Launch Termux",
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    )
}

