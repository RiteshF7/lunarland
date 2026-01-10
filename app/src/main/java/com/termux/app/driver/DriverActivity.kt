package com.termux.app.driver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lunar.land.ui.core.ui.NavigationButton
import lunar.land.ui.core.ui.TermuxButton
import lunar.land.ui.core.theme.LunarTheme
import com.termux.app.TermuxActivity
import com.termux.app.agent.TaskExecutorAgentActivity
import com.termux.app.backup.BackupTestActivity
import com.termux.app.BootstrapSetupActivity
import com.termux.shared.logger.Logger
import lunar.land.launcher.activity.AppDrawerActivity
import lunar.land.launcher.activity.LunarHomeScreenActivity

/**
 * Main driver activity that provides navigation buttons to various app features.
 * Uses Compose UI for modern, declarative interface.
 */
class DriverActivity : ComponentActivity() {
    private val LOG_TAG = "DriverActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: DriverViewModel = viewModel()
            DriverScreen(
                viewModel = viewModel,
                onLaunchTermux = { launchTermuxActivity() },
                onOpenTaskExecutor = { launchTaskExecutorActivity() },
                onLaunchLunarHomeScreen = { launchLunarHomeScreenActivity() },
                onPreviewAgent = { launchTaskExecutorActivity() },
                onTestAppDrawer = { launchAppDrawerActivity() },
                onTestBackup = { launchBackupTestActivity() },
                onBootstrapSetup = { launchBootstrapSetupActivity() },
                onOpenPortalConfig = { launchPortalSettingsActivity() },
                onTestPortalEndpoints = { launchPortalEndpointTestActivity() }
            )
        }
    }

    private fun launchTermuxActivity() {
        val intent = Intent(this, TermuxActivity::class.java)
        startActivity(intent)
    }

    private fun launchTaskExecutorActivity() {
        val intent = Intent(this, TaskExecutorAgentActivity::class.java)
        intent.putExtra("GOOGLE_API_KEY", com.termux.BuildConfig.GOOGLE_API_KEY)
        startActivity(intent)
    }

    private fun launchLunarHomeScreenActivity() {
        val intent = Intent(this, LunarHomeScreenActivity::class.java)
        startActivity(intent)
    }

    private fun launchAppDrawerActivity() {
        val intent = Intent(this, AppDrawerActivity::class.java)
        startActivity(intent)
    }

    private fun launchBackupTestActivity() {
        val intent = Intent(this, BackupTestActivity::class.java)
        startActivity(intent)
    }

    private fun launchBootstrapSetupActivity() {
        val intent = Intent(this, BootstrapSetupActivity::class.java)
        startActivity(intent)
    }

    private fun launchPortalSettingsActivity() {
        val intent = Intent(this, com.droidrun.portal.ui.MainActivity::class.java)
        startActivity(intent)
    }

    private fun launchPortalEndpointTestActivity() {
        val intent = Intent(this, PortalEndpointTestActivity::class.java)
        startActivity(intent)
    }
}

@Composable
private fun DriverScreen(
    viewModel: DriverViewModel,
    onLaunchTermux: () -> Unit,
    onOpenTaskExecutor: () -> Unit,
    onLaunchLunarHomeScreen: () -> Unit,
    onPreviewAgent: () -> Unit,
    onTestAppDrawer: () -> Unit,
    onTestBackup: () -> Unit,
    onBootstrapSetup: () -> Unit,
    onOpenPortalConfig: () -> Unit,
    onTestPortalEndpoints: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Check bootstrap status on first composition
    LaunchedEffect(Unit) {
        viewModel.checkBootstrapStatus(context)
    }

    // Use LunarTheme background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LunarTheme.BackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status text
            Text(
                text = uiState.statusText,
                color = LunarTheme.TextPrimary,
                style = LunarTheme.Typography.BodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation buttons using AppItem-style components
            TermuxButton(
                onClick = onLaunchTermux,
                enabled = uiState.isBootstrapReady
            )

            NavigationButton(
                text = "Open Task Executor",
                onClick = onOpenTaskExecutor
            )

            NavigationButton(
                text = "Launch Lunar UI HomeScreen",
                onClick = onLaunchLunarHomeScreen
            )

            NavigationButton(
                text = "Preview AI Agent Sphere",
                onClick = onPreviewAgent
            )

            NavigationButton(
                text = "Test App Drawer UI",
                onClick = onTestAppDrawer
            )

            NavigationButton(
                text = "Test Backup Download/Restore",
                onClick = onTestBackup
            )

            NavigationButton(
                text = "Bootstrap Setup",
                onClick = onBootstrapSetup
            )

            NavigationButton(
                text = "Portal Dashboard",
                onClick = onOpenPortalConfig
            )

            NavigationButton(
                text = "Test Portal Endpoints",
                onClick = onTestPortalEndpoints
            )
        }
    }
}


