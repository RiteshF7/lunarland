package lunar.land.ui.core.homescreen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> {
                // Home Screen (default page)
                HomeScreenContent(
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

