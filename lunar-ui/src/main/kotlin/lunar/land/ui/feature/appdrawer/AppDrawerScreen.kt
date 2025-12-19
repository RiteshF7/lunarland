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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lunar.land.ui.R
import androidx.compose.foundation.lazy.itemsIndexed
import lunar.land.ui.core.ui.SearchField
import lunar.land.ui.core.ui.extensions.launchApp
import lunar.land.ui.core.ui.extensions.onSwipeDown
import lunar.land.ui.feature.appdrawer.PatternedAppRow
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
        
        // Memoize row division - only recompute when filtered apps change
        // Must be outside LazyColumn content block
        val rows = remember(uiState.filteredApps) {
            uiState.filteredApps.getPatternedRows()
        }
        
        // Stable callback reference to prevent unnecessary recompositions
        // Must be outside LazyColumn content block
        val stableLaunchApp = remember(context) {
            { appInfo: AppInfo ->
                context.launchApp(app = appInfo.app)
            }
        }
        
        // Check if we're at the top of the list
        // Use derivedStateOf for efficient recomposition tracking
        val isAtTop = remember {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
        
        // Performance monitoring: Track scroll performance
        // This helps identify performance issues in production
        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                // Log scroll end metrics for performance analysis
                // Only log when not scrolling to avoid performance impact
                val visibleItemsCount = listState.layoutInfo.visibleItemsInfo.size
                val firstVisibleIndex = listState.firstVisibleItemIndex
                // Uncomment for debugging:
                // android.util.Log.d("AppDrawer", "Scroll ended: visibleItems=$visibleItemsCount, firstIndex=$firstVisibleIndex")
            }
        }
        
        // Nested scroll connection to detect swipe down at top
        // Only handle vertical swipes to avoid interfering with horizontal pager gestures
        val nestedScrollConnection = remember(onSwipeDownToClose) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Only handle vertical swipes (ignore horizontal)
                    // If at top and trying to scroll down (positive y), consume it and close drawer
                    if (isAtTop.value && 
                        available.y > 0 && 
                        kotlin.math.abs(available.y) > kotlin.math.abs(available.x) && // Prefer vertical over horizontal
                        source == NestedScrollSource.UserInput) {
                        onSwipeDownToClose?.invoke()
                        return Offset(0f, available.y) // Only consume vertical component
                    }
                    return Offset.Zero
                }
            }
        }
        
        // Scroll to top (app list) immediately when drawer opens - only once
        LaunchedEffect(Unit) {
            listState.scrollToItem(0)
        }
        
        // Use Column to keep search bar fixed at bottom
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Scrollable app list - takes up available space
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // App list - lazy loaded with patterned grid
                // Use LazyColumn.items() directly for optimal performance
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
                    // Use items() for true lazy loading - only visible rows are composed
                    // Use stable keys based on first app's package name in each row
                    // This ensures proper item tracking even when list updates
                    itemsIndexed(
                        items = rows,
                        key = { index, rowApps ->
                            // Use first app's package name as key for stability
                            // If row is empty, fall back to index
                            rowApps.firstOrNull()?.app?.packageName ?: "row_$index"
                        }
                    ) { _, rowApps ->
                        PatternedAppRow(
                            rowApps = rowApps,
                            onAppClick = stableLaunchApp,
                            horizontalSpacing = 14.dp
                        )
                    }
                }
            }
            
            // Search bar fixed at the bottom - always visible
            SearchField(
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(id = R.string.search),
                query = uiState.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                paddingValues = PaddingValues(horizontal = 0.dp, vertical = 12.dp)
            )
        }
    }
}

/**
 * Converts AppInfo to AppItemData for use with AppItem composable.
 * Generates dark-themed colors optimized for black background.
 * Optimized to minimize Color object creation overhead.
 */
internal fun AppInfo.toAppItemData(): AppItemData {
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
