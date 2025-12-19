package lunar.land.ui.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import lunar.land.ui.core.model.Theme
import lunar.land.ui.core.ui.controller.setSystemBarsColor
import lunar.land.ui.core.ui.providers.LocalSystemUiController
import lunar.land.ui.core.theme.data.Typography
import lunar.land.ui.core.theme.data.darkColors
import lunar.land.ui.core.theme.data.lightColors

@Composable
fun LauncherTheme(
    currentTheme: Theme,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val systemUiController = LocalSystemUiController.current

    val colorScheme = when (currentTheme) {
        Theme.NOT_WHITE -> lightColors
        Theme.SAID_DARK -> darkColors
        Theme.FOLLOW_SYSTEM -> if (useDarkTheme) darkColors else lightColors
    }

    LaunchedEffect(key1 = systemUiController, key2 = colorScheme) {
        // Set status bar color
        systemUiController.setStatusBarColor(color = colorScheme.surface)
        // Set navigation bar to transparent to remove grey background below page indicators
        systemUiController.setNavigationBarColor(color = Color.Transparent, darkIcons = false)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

