package lunar.land.launcher.core.homescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.pager.ExperimentalPagerApi
import lunar.land.launcher.feature.homescreen.HomeScreen
import lunar.land.launcher.feature.appdrawer.AppDrawerScreen
import lunar.land.launcher.feature.appdrawer.AppDrawerViewModel
import lunar.land.ui.core.theme.LunarTheme

/**
 * Wrapper that provides swipeable navigation between two screens:
 * - Page 0: HomeScreen (middle/home screen) - default
 * - Page 1: AppDrawerScreen (right/app drawer)
 * 
 * Swipe left (right-to-left gesture) from HomeScreen to access AppDrawerScreen.
 * 
 * Uses Accompanist Pager library with custom page indicators.
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeScreenPager(
    modifier: Modifier = Modifier,
    appDrawerViewModel: AppDrawerViewModel,
    showIndicators: Boolean = true
) {
    // Page order: HomeScreen (0), AppDrawer (1)
    // Start on HomeScreen (page 0)
    val pagerState = rememberPagerState(
        initialPage = 0 // Start on HomeScreen (page 0)
    )
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        HorizontalPager(
            count = 2,
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) { page ->
            // Use key to prevent unnecessary recomposition during swipes
            androidx.compose.runtime.key(page) {
                when (page) {
                    0 -> {
                        // Home Screen (middle/home screen) - default
                        HomeScreen(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> {
                        // App Drawer Screen (right/app drawer)
                        // Swipe left from HomeScreen to access
                        AppDrawerScreen(
                            viewModel = appDrawerViewModel,
                            onSwipeDownToClose = null // No swipe down to close in pager mode
                        )
                    }
                }
            }
        }
        
        // Custom page indicators at the bottom - minimal, no background
        // Use derivedStateOf to minimize recomposition during swipes
        if (showIndicators) {
            val currentPage by remember {
                derivedStateOf { pagerState.currentPage }
            }
            
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp), // Minimal bottom padding only
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(2) { index ->
                    val isSelected = currentPage == index
                    val color = if (isSelected) {
                        LunarTheme.TextPrimary
                    } else {
                        LunarTheme.TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .size(5.dp) // Smaller dots
                            .clip(CircleShape)
                            .background(color) // Just the dot color, no container background
                    )
                }
            }
        }
    }
}

