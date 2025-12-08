package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lunar.land.ui.R
import lunar.land.ui.core.ui.SearchField
import lunar.land.ui.core.ui.extensions.launchApp
import lunar.land.ui.feature.favorites.ui.StaggeredFlowRow
import lunar.land.ui.manager.model.AppInfo

/**
 * Screen demonstrating the App Drawer with modern 3D effects and glow.
 * Shows all installed apps with search functionality.
 */
@Composable
fun AppDrawerScreen(
    viewModel: AppDrawerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    AppDrawerContainer {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search bar at the top
            SearchField(
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(id = R.string.search),
                query = uiState.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                paddingValues = PaddingValues(horizontal = 0.dp, vertical = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // App list
            if (uiState.isLoading) {
                // TODO: Add loading indicator
            } else {
                StaggeredFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 14.dp,
                    crossAxisSpacing = 14.dp
                ) {
                    uiState.filteredApps.forEach { appInfo ->
                        val appItemData = appInfo.toAppItemData()
                        AppItem(
                            appData = appItemData,
                            onClick = { context.launchApp(app = appInfo.app) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Converts AppInfo to AppItemData for use with AppItem composable.
 * Uses the actual app icon and generates modern, vibrant, light colors with strong glow.
 */
private fun AppInfo.toAppItemData(): AppItemData {
    val colorInt = color
    val color = Color(colorInt)
    
    // Create vibrant, very light background color for modern 3D look
    // Much lighter and more saturated for a glowing, vibrant appearance
    val backgroundColor = Color(
        red = (color.red * 0.25f + 0.55f).coerceIn(0f, 1f),
        green = (color.green * 0.25f + 0.55f).coerceIn(0f, 1f),
        blue = (color.blue * 0.25f + 0.55f).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    // Use vibrant, saturated color for text
    val textColor = Color(
        red = (color.red * 0.8f + 0.15f).coerceIn(0f, 1f),
        green = (color.green * 0.8f + 0.15f).coerceIn(0f, 1f),
        blue = (color.blue * 0.8f + 0.15f).coerceIn(0f, 1f),
        alpha = 0.95f
    )
    
    // Stronger, more vibrant glow color for 3D effect
    val glowColor = Color(
        red = (color.red * 0.9f + 0.1f).coerceIn(0f, 1f),
        green = (color.green * 0.9f + 0.1f).coerceIn(0f, 1f),
        blue = (color.blue * 0.9f + 0.1f).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    return AppItemData(
        name = app.displayName,
        iconDrawable = icon, // Use the actual app icon
        backgroundColor = backgroundColor,
        textColor = textColor,
        glowColor = glowColor,
        isWide = false
    )
}
