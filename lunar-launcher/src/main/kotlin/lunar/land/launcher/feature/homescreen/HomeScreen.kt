package lunar.land.launcher.feature.homescreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Main HomeScreen composable that orchestrates all UI components.
 * This follows the same design pattern as TaskExecutorAgentScreen with:
 * - Same background colors and gradient
 * - Same Manrope font family
 * - Modular component architecture
 * - All logic centralized in HomeScreenContent
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onClockClick: (() -> Unit)? = null,
    onLunarCalendarClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background with radial gradient matching TaskExecutorAgentScreen
        HomeScreenBackground(
            modifier = Modifier.fillMaxSize()
        )
        
        // Main content area - contains all the home screen widgets and logic
        HomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            onClockClick = onClockClick,
            onLunarCalendarClick = onLunarCalendarClick
        )
    }
}

