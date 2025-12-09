package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lunar.land.ui.R
import lunar.land.ui.core.ui.SearchField
import lunar.land.ui.core.ui.extensions.launchApp
import lunar.land.ui.core.ui.extensions.onSwipeDown
import lunar.land.ui.feature.favorites.ui.StaggeredFlowRow
import lunar.land.ui.manager.model.AppInfo

/**
 * Screen demonstrating the App Drawer with modern 3D effects and glow.
 * Shows all installed apps with search functionality.
 */
@Composable
fun AppDrawerScreen(
    viewModel: AppDrawerViewModel = viewModel(),
    onSwipeDown: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    AppDrawerContainer {
        val listState = rememberLazyListState()
        
        // Pre-compute app item data to avoid recomposition overhead
        // Use lazy evaluation - only compute when needed
        val appItemsData = remember(uiState.filteredApps) {
            if (uiState.filteredApps.isEmpty()) {
                emptyList()
            } else {
                uiState.filteredApps.map { appInfo ->
                    appInfo to appInfo.toAppItemData()
                }
            }
        }
        
        // Check if we're at the top of the list
        val isAtTop = remember {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
        
        // Scroll to top (search bar) immediately when drawer opens - no animation delay
        LaunchedEffect(uiState.allApps.isNotEmpty()) {
            if (uiState.allApps.isNotEmpty()) {
                // Use scrollToItem (instant) instead of animateScrollToItem
                listState.scrollToItem(0)
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onSwipeDown != null && isAtTop.value) {
                        Modifier.onSwipeDown(enabled = true) {
                            onSwipeDown()
                        }
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Search bar at the top
            item(key = "search") {
                SearchField(
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(id = R.string.search),
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    paddingValues = PaddingValues(horizontal = 0.dp, vertical = 12.dp)
                )
            }
            
            // App list - lazy loaded with staggered grid
            if (uiState.isLoading && appItemsData.isEmpty()) {
                // Show loading indicator only on first load
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        // Loading indicator can be added here
                    }
                }
            } else {
                // Use StaggeredFlowRow in a single item for better layout
                item(key = "apps_grid") {
                    StaggeredFlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 14.dp,
                        crossAxisSpacing = 14.dp
                    ) {
                        appItemsData.forEach { (appInfo, appItemData) ->
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
