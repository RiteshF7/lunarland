package com.termux.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.BuildConfig
import com.termux.app.TaskExecutorViewModel
import com.termux.app.TaskExecutorViewModelFactory
import lunar.land.ui.core.homescreen.HomeScreenPager
import lunar.land.ui.core.model.Theme
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.ui.providers.ProvideSystemUiController
import lunar.land.ui.feature.taskexecagent.TaskExecutorAgentScreen

/**
 * Main launcher activity for the Lunar Home Screen.
 * This activity can be set as the default launcher/home screen.
 * 
 * Launcher features:
 * - Responds to HOME button press (handled by singleTask launch mode)
 * - Shows home screen with app drawer
 * - Handles app launching
 * - Single task mode ensures only one instance and brings to front on HOME press
 */
class LunarHomeScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Get Google API key from BuildConfig
        val googleApiKey = BuildConfig.GOOGLE_API_KEY ?: ""
        
        setContent {
            ProvideSystemUiController {
                LauncherTheme(currentTheme = Theme.FOLLOW_SYSTEM) {
                    val taskExecutorViewModel: TaskExecutorViewModel = viewModel(
                        factory = TaskExecutorViewModelFactory(
                            this@LunarHomeScreenActivity,
                            googleApiKey
                        )
                    )
                    HomeScreenPager(
                        modifier = Modifier.fillMaxSize(),
                        taskExecutorContent = {
                            TaskExecutorAgentScreen(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = taskExecutorViewModel
                            )
                        }
                    )
                }
            }
        }
    }
}


