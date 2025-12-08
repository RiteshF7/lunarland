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
            // Add space on top of search bar
            Spacer(modifier = Modifier.height(24.dp))
            
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
 * Generates dark-themed colors optimized for black background.
 */
private fun AppInfo.toAppItemData(): AppItemData {
    val colorInt = color
    val color = Color(colorInt)
    
    // Create vibrant background that matches app's color theme
    // Use more of the app's actual color for better visual harmony
    val baseDark = 0.12f  // Base dark value for depth
    val colorContribution = 0.35f  // Strong color contribution for theme matching
    
    // Blend app color more prominently to match content
    val backgroundColor = Color(
        red = (color.red * colorContribution + baseDark).coerceIn(0f, 1f),
        green = (color.green * colorContribution + baseDark).coerceIn(0f, 1f),
        blue = (color.blue * colorContribution + baseDark).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    // Light text color with subtle color tint for readability on dark background
    val textColor = Color(
        red = (color.red * 0.3f + 0.85f).coerceIn(0f, 1f),
        green = (color.green * 0.3f + 0.85f).coerceIn(0f, 1f),
        blue = (color.blue * 0.3f + 0.85f).coerceIn(0f, 1f),
        alpha = 0.95f
    )
    
    // Glow color with more saturation for visibility on dark background
    val glowColor = Color(
        red = (color.red * 0.6f + 0.3f).coerceIn(0f, 1f),
        green = (color.green * 0.6f + 0.3f).coerceIn(0f, 1f),
        blue = (color.blue * 0.6f + 0.3f).coerceIn(0f, 1f),
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
