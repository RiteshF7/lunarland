package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lunar.land.ui.core.theme.LunarTheme
import lunar.land.ui.feature.homescreen.HomeScreenBackground

/**
 * Modern container for the app drawer with homescreenbg.jpeg background.
 * Uses the same background image as the home screen for visual consistency.
 * 
 * @param modifier Modifier for styling and layout
 * @param content The app drawer content
 */
@Composable
fun AppDrawerContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        // Background with homescreenbg.jpeg image - same as home screen
        HomeScreenBackground(
            modifier = Modifier.fillMaxSize()
        )
        
        // Content container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = LunarTheme.Spacing.ExtraLarge, vertical = LunarTheme.Spacing.XXLarge)
        ) {
            content()
        }
    }
}
