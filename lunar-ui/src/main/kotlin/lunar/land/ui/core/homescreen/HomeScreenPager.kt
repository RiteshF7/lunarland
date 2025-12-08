package lunar.land.ui.core.homescreen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import lunar.land.ui.core.ui.extensions.onSwipeRight
import lunar.land.ui.feature.homescreen.HomeScreen

/**
 * Wrapper that provides swipeable navigation between HomeScreen and TaskExecutorScreen.
 * Swipe left (right to left gesture) from HomeScreen to access TaskExecutorScreen.
 * Swipe right (left to right gesture) from TaskExecutorScreen to return to HomeScreen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenPager(
    modifier: Modifier = Modifier,
    taskExecutorContent: @Composable () -> Unit
) {
    // Normal page order: HomeScreen on page 0, TaskExecutorScreen on page 1
    // Swipe left (right-to-left gesture) from HomeScreen to access TaskExecutorScreen
    // Swipe right (left-to-right gesture) from TaskExecutorScreen to return to HomeScreen
    val pagerState = rememberPagerState(
        initialPage = 0, // Start on HomeScreen (page 0)
        pageCount = { 2 }
    )
    
    val coroutineScope = rememberCoroutineScope()
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .onSwipeRight(enabled = pagerState.currentPage == 0) {
                // Custom swipe right handler: swipe right from HomeScreen to go to TaskExecutorScreen
                coroutineScope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }
    ) { page ->
        when (page) {
            0 -> {
                // Home Screen (default page) - using new modular homescreen feature
                HomeScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
            1 -> {
                // Task Executor Screen (swipe left from HomeScreen to access, swipe right to return)
                taskExecutorContent()
            }
        }
    }
}

