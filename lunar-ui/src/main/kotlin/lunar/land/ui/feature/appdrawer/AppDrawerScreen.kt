package lunar.land.ui.feature.appdrawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
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
    viewModel: AppDrawerViewModel,
    onSwipeDownToClose: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    AppDrawerContainer {
        val listState = rememberLazyListState()
        
        // Check if we're at the top of the list
        val isAtTop = remember {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
        
        // Nested scroll connection to detect swipe down at top
        val nestedScrollConnection = remember(onSwipeDownToClose) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // If at top and trying to scroll down (positive y), consume it and close drawer
                    if (isAtTop.value && available.y > 0 && source == NestedScrollSource.UserInput) {
                        onSwipeDownToClose?.invoke()
                        return available // Consume the scroll
                    }
                    return Offset.Zero
                }
            }
        }
        
        // Scroll to top (search bar) immediately when drawer opens - only once
        LaunchedEffect(Unit) {
            listState.scrollToItem(0)
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onSwipeDownToClose != null) {
                        Modifier.nestedScroll(nestedScrollConnection)
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
            if (uiState.isLoading && uiState.filteredApps.isEmpty()) {
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
                // Compute app item data lazily during rendering to avoid blocking UI
                item(key = "apps_grid") {
                    StaggeredFlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 14.dp,
                        crossAxisSpacing = 14.dp
                    ) {
                        uiState.filteredApps.forEach { appInfo ->
                            AppItem(
                                appData = appInfo.toAppItemData(),
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
 * Optimized to minimize Color object creation overhead.
 */
private fun AppInfo.toAppItemData(): AppItemData {
    // Extract RGB components directly from color int to avoid Color object creation
    val r = android.graphics.Color.red(color) / 255f
    val g = android.graphics.Color.green(color) / 255f
    val b = android.graphics.Color.blue(color) / 255f
    
    // Pre-compute constants
    val baseDark = 0.12f
    val colorContribution = 0.35f
    val textBase = 0.85f
    val textTint = 0.3f
    val glowBase = 0.3f
    val glowTint = 0.6f
    
    // Compute colors directly without intermediate Color objects
    val backgroundColor = Color(
        red = (r * colorContribution + baseDark).coerceIn(0f, 1f),
        green = (g * colorContribution + baseDark).coerceIn(0f, 1f),
        blue = (b * colorContribution + baseDark).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    val textColor = Color(
        red = (r * textTint + textBase).coerceIn(0f, 1f),
        green = (g * textTint + textBase).coerceIn(0f, 1f),
        blue = (b * textTint + textBase).coerceIn(0f, 1f),
        alpha = 0.95f
    )
    
    val glowColor = Color(
        red = (r * glowTint + glowBase).coerceIn(0f, 1f),
        green = (g * glowTint + glowBase).coerceIn(0f, 1f),
        blue = (b * glowTint + glowBase).coerceIn(0f, 1f),
        alpha = 1f
    )
    
    return AppItemData(
        name = app.displayName,
        iconDrawable = icon,
        backgroundColor = backgroundColor,
        textColor = textColor,
        glowColor = glowColor,
        isWide = false
    )
}
