package com.termux.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
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
        
        setContent {
            ProvideSystemUiController {
                LauncherTheme(currentTheme = Theme.FOLLOW_SYSTEM) {
                    HomeScreenPager(
                        modifier = Modifier.fillMaxSize(),
                        taskExecutorContent = {
                            TaskExecutorAgentScreen(
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                }
            }
        }
    }
}


