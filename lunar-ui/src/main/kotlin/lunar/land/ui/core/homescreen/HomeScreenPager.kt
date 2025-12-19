package lunar.land.ui.core.homescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.pager.ExperimentalPagerApi
import lunar.land.ui.feature.homescreen.HomeScreen
import lunar.land.ui.feature.appdrawer.AppDrawerScreen
import lunar.land.ui.feature.appdrawer.AppDrawerViewModel
import lunar.land.ui.core.theme.LunarTheme

/**
 * Wrapper that provides swipeable navigation between three screens:
 * - Page 0: TaskExecutorAgentScreen (left/agent screen)
 * - Page 1: HomeScreen (middle/home screen) - default
 * - Page 2: AppDrawerScreen (right/app drawer)
 * 
 * Swipe left (right-to-left gesture) from HomeScreen to access AppDrawerScreen.
 * Swipe right (left-to-right gesture) from HomeScreen to access TaskExecutorScreen.
 * 
 * Uses Accompanist Pager library with custom page indicators.
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeScreenPager(
    modifier: Modifier = Modifier,
    taskExecutorContent: @Composable () -> Unit,
    appDrawerViewModel: AppDrawerViewModel,
    showIndicators: Boolean = true
) {
    // Page order: TaskExecutor (0), HomeScreen (1), AppDrawer (2)
    // Start on HomeScreen (page 1) in the middle
    val pagerState = rememberPagerState(
        initialPage = 1 // Start on HomeScreen (page 1) in the middle
    )
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        HorizontalPager(
            count = 3,
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    // Task Executor Screen (left/agent screen)
                    // Swipe right from HomeScreen to access
                    taskExecutorContent()
                }
                1 -> {
                    // Home Screen (middle/home screen) - default
                    HomeScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                }
                2 -> {
                    // App Drawer Screen (right/app drawer)
                    // Swipe left from HomeScreen to access
                    AppDrawerScreen(
                        viewModel = appDrawerViewModel,
                        onSwipeDownToClose = null // No swipe down to close in pager mode
                    )
                }
            }
        }
        
        // Custom page indicators at the bottom
        if (showIndicators) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { index ->
                    val isSelected = pagerState.currentPage == index
                    val color = if (isSelected) {
                        LunarTheme.TextPrimary
                    } else {
                        LunarTheme.TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

