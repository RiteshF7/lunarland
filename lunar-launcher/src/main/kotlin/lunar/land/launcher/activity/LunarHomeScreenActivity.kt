package lunar.land.launcher.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import lunar.land.launcher.core.homescreen.HomeScreenPager
import lunar.land.ui.core.model.Theme
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.ui.providers.ProvideSystemUiController
import lunar.land.ui.manager.AppStateManager
import lunar.land.launcher.feature.appdrawer.AppDrawerViewModel

/**
 * Main launcher activity for the Lunar Home Screen.
 * This activity can be set as the default launcher/home screen.
 * 
 * Launcher features:
 * - Responds to HOME button press (handled by singleTask launch mode)
 * - Shows home screen with app drawer
 * - Handles app launching
 * - Single task mode ensures only one instance and brings to front on HOME press
 * - Two-panel layout: Home (middle), App Drawer (right)
 */
class LunarHomeScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            ProvideSystemUiController {
                LauncherTheme(currentTheme = Theme.FOLLOW_SYSTEM) {
                    // Initialize AppStateManager early - this starts caching immediately
                    // Using a consistent key ensures the same instance is reused
                    val appStateManager: AppStateManager = viewModel(
                        key = "app_state_manager"
                    )
                    
                    // Initialize AppDrawerViewModel
                    val appDrawerViewModel: AppDrawerViewModel = viewModel(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return AppDrawerViewModel(
                                    this@LunarHomeScreenActivity.application,
                                    appStateManager
                                ) as T
                            }
                        }
                    )
                    
                    HomeScreenPager(
                        modifier = Modifier.fillMaxSize(),
                        appDrawerViewModel = appDrawerViewModel
                    )
                }
            }
        }
    }
}


