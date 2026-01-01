package com.termux.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.app.TaskExecutorViewModel
import com.termux.app.TaskExecutorViewModelFactory
import lunar.land.ui.core.model.Theme
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.ui.providers.ProvideSystemUiController
import lunar.land.ui.feature.taskexecagent.TaskExecutorAgentScreen

class TaskExecutorAgentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Get Google API key from intent extras or use empty string
        val googleApiKey = intent.getStringExtra("GOOGLE_API_KEY") ?: ""
        
        setContent {
            ProvideSystemUiController {
                LauncherTheme(currentTheme = Theme.FOLLOW_SYSTEM) {
                    val viewModelInstance: TaskExecutorViewModel = viewModel(
                        factory = TaskExecutorViewModelFactory(
                            this@TaskExecutorAgentActivity,
                            googleApiKey
                        )
                    )
                    TaskExecutorAgentScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        viewModel = viewModelInstance
                    )
                }
            }
        }
    }
}
