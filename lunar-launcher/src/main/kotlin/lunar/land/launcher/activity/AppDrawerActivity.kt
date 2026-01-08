package lunar.land.launcher.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import lunar.land.ui.core.model.Theme
import lunar.land.ui.core.theme.LauncherTheme
import lunar.land.ui.core.ui.providers.ProvideSystemUiController
import lunar.land.launcher.feature.appdrawer.AppDrawerScreen
import lunar.land.ui.manager.AppStateManager

class AppDrawerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            ProvideSystemUiController {
                LauncherTheme(currentTheme = Theme.FOLLOW_SYSTEM) {
                    val appStateManager: AppStateManager = viewModel()
                    AppDrawerScreen(
                        viewModel = viewModel(
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    return lunar.land.launcher.feature.appdrawer.AppDrawerViewModel(
                                        this@AppDrawerActivity.application,
                                        appStateManager
                                    ) as T
                                }
                            }
                        )
                    )
                }
            }
        }
    }
}

