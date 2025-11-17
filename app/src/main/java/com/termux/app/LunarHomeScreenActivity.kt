package com.termux.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import lunar.land.ui.core.homescreen.HomeScreenContent
import lunar.land.ui.core.model.Theme
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.ui.providers.ProvideSystemUiController

class LunarHomeScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            ProvideSystemUiController {
                LauncherTheme(currentTheme = Theme.FOLLOW_SYSTEM) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        HomeScreenContent(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

