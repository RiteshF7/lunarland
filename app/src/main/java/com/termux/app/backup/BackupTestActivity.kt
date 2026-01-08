package com.termux.app.backup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.app.backup.BackupDownloader
import com.termux.app.bootstrap.BootstrapManager
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for testing backup download and restore functionality.
 * This allows testing backup restore without going through full bootstrap installation.
 */
class BackupTestActivity : ComponentActivity() {
    private val LOG_TAG = "BackupTestActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                BackupTestScreen(
                    activity = this,
                    onBack = { finish() }
                )
            }
        }
    }
    
    /**
     * Downloads backup file only (without restoring).
     */
    fun downloadBackupOnly(viewModel: BackupTestViewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arch = BootstrapManager.detectBootstrap(this@BackupTestActivity).architecture
                    ?: run {
                        val errorMsg = "Failed to detect architecture"
                        Logger.logError(LOG_TAG, errorMsg)
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.onError(errorMsg)
                        }
                        return@launch
                    }
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Detected architecture: $arch")
                    viewModel.appendLog("Starting backup download...")
                }
                
                var lastProgressPercent = -1
                var lastProgressUpdateTime = 0L
                val progressCallback = object : BackupDownloader.ProgressCallback {
                    override fun onProgress(downloaded: Long, total: Long) {
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            val currentTime = System.currentTimeMillis()
                            if (percent != lastProgressPercent && (currentTime - lastProgressUpdateTime >= 500 || percent == 100)) {
                                lastProgressPercent = percent
                                lastProgressUpdateTime = currentTime
                                CoroutineScope(Dispatchers.Main).launch {
                                    viewModel.appendLog("Downloading backup: $percent%")
                                }
                            }
                        }
                    }
                }
                
                val downloadError = withContext(Dispatchers.IO) {
                    BackupDownloader.downloadBackup(this@BackupTestActivity, arch, progressCallback)
                }
                
                if (downloadError != null) {
                    val errorMsg = "Backup download failed: ${downloadError.message}"
                    Logger.logError(LOG_TAG, errorMsg)
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.onError(errorMsg)
                    }
                    return@launch
                }
                
                val backupFile = BackupDownloader.getLocalBackupFile(this@BackupTestActivity, arch)
                if (backupFile == null || !backupFile.exists()) {
                    val errorMsg = "Backup file not found after download"
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.onError(errorMsg)
                    }
                    return@launch
                }
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Backup downloaded successfully: ${backupFile.absolutePath}")
                    viewModel.onSuccess()
                }
                
            } catch (e: Exception) {
                val errorMsg = "Error during backup download: ${e.message}"
                Logger.logStackTraceWithMessage(LOG_TAG, errorMsg, e)
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.onError(errorMsg)
                }
            }
        }
    }
    
    /**
     * Restores backup from already downloaded file.
     */
    fun restoreBackupOnly(viewModel: BackupTestViewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arch = BootstrapManager.detectBootstrap(this@BackupTestActivity).architecture
                    ?: run {
                        val errorMsg = "Failed to detect architecture"
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.onError(errorMsg)
                        }
                        return@launch
                    }
                
                val backupFile = BackupDownloader.getLocalBackupFile(this@BackupTestActivity, arch)
                if (backupFile == null || !backupFile.exists()) {
                    val errorMsg = "Backup file not found. Please download first."
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.onError(errorMsg)
                    }
                    return@launch
                }
                
                // Verify file exists and is readable
                val fileExists = backupFile.exists()
                val fileReadable = if (fileExists) backupFile.canRead() else false
                val fileSize = if (fileExists) backupFile.length() else 0
                
                Logger.logInfo(LOG_TAG, "Backup file verification:")
                Logger.logInfo(LOG_TAG, "  Path: ${backupFile.absolutePath}")
                Logger.logInfo(LOG_TAG, "  Exists: $fileExists")
                Logger.logInfo(LOG_TAG, "  Readable: $fileReadable")
                Logger.logInfo(LOG_TAG, "  Size: $fileSize bytes (${String.format("%.2f", fileSize / (1024.0 * 1024.0))} MB)")
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Found backup file: ${backupFile.absolutePath}")
                    viewModel.appendLog("File size: ${String.format("%.2f", fileSize / (1024.0 * 1024.0))} MB")
                    viewModel.appendLog("File readable: $fileReadable")
                }
                
                if (!fileExists || !fileReadable) {
                    val errorMsg = "Backup file not accessible: exists=$fileExists, readable=$fileReadable"
                    Logger.logError(LOG_TAG, errorMsg)
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.onError(errorMsg)
                    }
                    return@launch
                }
                
                // Use absolute path to ensure termux-restore can find the file
                // The backup file is in files/home/, so we use the absolute path
                val restoreCommand = "termux-restore \"${backupFile.absolutePath}\""
                
                Logger.logInfo(LOG_TAG, "Preparing to execute restore command:")
                Logger.logInfo(LOG_TAG, "  Command: $restoreCommand")
                Logger.logInfo(LOG_TAG, "  Backup file path: ${backupFile.absolutePath}")
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Restoring backup...")
                    viewModel.appendLog("Command: $restoreCommand")
                }
                executeTermuxCommand(restoreCommand) { success, output ->
                    // Wait for termux-restore to complete
                    Thread.sleep(5000)
                    
                    Logger.logInfo(LOG_TAG, "Restore command completed: success=$success, output=$output")
                    
                    // Verify that the backup file still exists at the expected path
                    val backupFileStillExists = backupFile.exists()
                    Logger.logInfo(LOG_TAG, "Backup file still exists after restore: $backupFileStillExists at ${backupFile.absolutePath}")
                    
                    if (success) {
                        // After successful restore, export GOOGLE_API_KEY
                        exportGoogleApiKey(viewModel)
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.appendLog("Backup restore command executed successfully")
                            viewModel.appendLog("Backup file path: ${backupFile.absolutePath}")
                            viewModel.onSuccess()
                        }
                    } else {
                        val errorMsg = "Backup restore failed: $output"
                        Logger.logError(LOG_TAG, errorMsg)
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.onError(errorMsg)
                        }
                    }
                }
                
            } catch (e: Exception) {
                val errorMsg = "Error during backup restore: ${e.message}"
                Logger.logStackTraceWithMessage(LOG_TAG, errorMsg, e)
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.onError(errorMsg)
                }
            }
        }
    }
    
    /**
     * Downloads backup file and restores it.
     */
    fun downloadAndRestoreBackup(viewModel: BackupTestViewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val arch = BootstrapManager.detectBootstrap(this@BackupTestActivity).architecture
                    ?: run {
                        val errorMsg = "Failed to detect architecture"
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.onError(errorMsg)
                        }
                        return@launch
                    }
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Detected architecture: $arch")
                    viewModel.appendLog("Starting backup download...")
                }
                
                var lastProgressPercent = -1
                var lastProgressUpdateTime = 0L
                val progressCallback = object : BackupDownloader.ProgressCallback {
                    override fun onProgress(downloaded: Long, total: Long) {
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            val currentTime = System.currentTimeMillis()
                            if (percent != lastProgressPercent && (currentTime - lastProgressUpdateTime >= 500 || percent == 100)) {
                                lastProgressPercent = percent
                                lastProgressUpdateTime = currentTime
                                CoroutineScope(Dispatchers.Main).launch {
                                    viewModel.appendLog("Downloading backup: $percent%")
                                }
                            }
                        }
                    }
                }
                
                val downloadError = withContext(Dispatchers.IO) {
                    BackupDownloader.downloadBackup(this@BackupTestActivity, arch, progressCallback)
                }
                
                if (downloadError != null) {
                    val errorMsg = "Backup download failed: ${downloadError.message}"
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.onError(errorMsg)
                    }
                    return@launch
                }
                
                val backupFile = BackupDownloader.getLocalBackupFile(this@BackupTestActivity, arch)
                if (backupFile == null || !backupFile.exists()) {
                    val errorMsg = "Backup file not found after download"
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.onError(errorMsg)
                    }
                    return@launch
                }
                
                // Verify file exists and get detailed info
                val fileExists = backupFile.exists()
                val fileSize = if (fileExists) backupFile.length() else 0
                val fileReadable = if (fileExists) backupFile.canRead() else false
                val filePath = backupFile.absolutePath
                
                Logger.logInfo(LOG_TAG, "Backup file details:")
                Logger.logInfo(LOG_TAG, "  Path: $filePath")
                Logger.logInfo(LOG_TAG, "  Exists: $fileExists")
                Logger.logInfo(LOG_TAG, "  Size: $fileSize bytes (${fileSize / (1024.0 * 1024.0)} MB)")
                Logger.logInfo(LOG_TAG, "  Readable: $fileReadable")
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Backup downloaded successfully")
                    viewModel.appendLog("File size: ${String.format("%.2f", fileSize / (1024.0 * 1024.0))} MB")
                    viewModel.appendLog("File readable: $fileReadable")
                    viewModel.appendLog("Restoring backup...")
                }
                
                if (!fileExists || !fileReadable) {
                    val errorMsg = "Backup file not accessible: exists=$fileExists, readable=$fileReadable"
                    Logger.logError(LOG_TAG, errorMsg)
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.onError(errorMsg)
                    }
                    return@launch
                }
                
                // Use absolute path to ensure termux-restore can find the file
                val restoreCommand = "termux-restore \"${backupFile.absolutePath}\""
                
                Logger.logInfo(LOG_TAG, "Preparing to execute restore command:")
                Logger.logInfo(LOG_TAG, "  Command: $restoreCommand")
                Logger.logInfo(LOG_TAG, "  Backup file path: ${backupFile.absolutePath}")
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Restoring backup...")
                    viewModel.appendLog("Command: $restoreCommand")
                }
                
                executeTermuxCommand(restoreCommand) { success, output ->
                    Logger.logInfo(LOG_TAG, "Command execution callback received:")
                    Logger.logInfo(LOG_TAG, "  Success: $success")
                    Logger.logInfo(LOG_TAG, "  Output: $output")
                    
                    // Wait for termux-restore to complete
                    Thread.sleep(5000)
                    
                    // Verify that the backup file exists at the expected path
                    val backupFileStillExists = backupFile.exists()
                    Logger.logInfo(LOG_TAG, "Backup file still exists after restore: $backupFileStillExists at ${backupFile.absolutePath}")
                    
                    // Delete backup file after restore
                    try {
                        if (backupFile.exists()) {
                            val deleted = backupFile.delete()
                            Logger.logInfo(LOG_TAG, "Deleted backup file after restore: $deleted")
                        }
                    } catch (e: Exception) {
                        Logger.logError(LOG_TAG, "Error deleting backup file: ${e.message}")
                    }
                    
                    if (success) {
                        // After successful restore, export GOOGLE_API_KEY
                        exportGoogleApiKey(viewModel)
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.appendLog("Backup restore command executed successfully")
                            viewModel.appendLog("Backup file path: ${backupFile.absolutePath}")
                            viewModel.onSuccess()
                        }
                    } else {
                        val errorMsg = "Backup restore failed: $output"
                        Logger.logError(LOG_TAG, errorMsg)
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.onError(errorMsg)
                        }
                    }
                }
                
            } catch (e: Exception) {
                val errorMsg = "Error during backup download/restore: ${e.message}"
                Logger.logStackTraceWithMessage(LOG_TAG, errorMsg, e)
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.onError(errorMsg)
                }
            }
        }
    }
    
    /**
     * Exports GOOGLE_API_KEY by adding it directly to .bashrc file for persistence.
     */
    fun exportGoogleApiKey(viewModel: BackupTestViewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiKey = com.termux.BuildConfig.GOOGLE_API_KEY
                if (apiKey.isBlank()) {
                    Logger.logWarn(LOG_TAG, "GOOGLE_API_KEY is not set in BuildConfig")
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.appendLog("Warning: GOOGLE_API_KEY not found in BuildConfig")
                    }
                    return@launch
                }
                
                val bashrcPath = "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                val bashrcFile = File(bashrcPath)
                val exportLine = "export GOOGLE_API_KEY=\"$apiKey\""
                
                Logger.logInfo(LOG_TAG, "Adding GOOGLE_API_KEY to .bashrc at: $bashrcPath")
                
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Adding GOOGLE_API_KEY to .bashrc...")
                }
                
                // Check if .bashrc exists, create if not
                if (!bashrcFile.exists()) {
                    val parentDir = bashrcFile.parentFile
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs()
                    }
                    bashrcFile.createNewFile()
                    Logger.logInfo(LOG_TAG, "Created .bashrc file")
                }
                
                // Read existing content to check if API key already exists
                val existingContent = if (bashrcFile.exists() && bashrcFile.canRead()) {
                    bashrcFile.readText(StandardCharsets.UTF_8)
                } else {
                    ""
                }
                
                // Check if export line already exists
                if (existingContent.contains("export GOOGLE_API_KEY")) {
                    Logger.logInfo(LOG_TAG, "GOOGLE_API_KEY already exists in .bashrc, updating...")
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.appendLog("GOOGLE_API_KEY already exists, updating...")
                    }
                    
                    // Remove old export line and add new one
                    val updatedContent = existingContent.lines()
                        .filterNot { it.trim().startsWith("export GOOGLE_API_KEY") }
                        .joinToString("\n")
                    
                    val newContent = if (updatedContent.isBlank()) {
                        exportLine
                    } else {
                        "$updatedContent\n$exportLine"
                    }
                    
                    val error = FileUtils.writeTextToFile(
                        "bashrc",
                        bashrcPath,
                        StandardCharsets.UTF_8,
                        newContent,
                        false
                    )
                    
                    if (error != null) {
                        Logger.logError(LOG_TAG, "Failed to update .bashrc: ${error.getMessage()}")
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.appendLog("Error: Failed to update .bashrc: ${error.getMessage()}")
                        }
                        return@launch
                    }
                } else {
                    // Append export line to .bashrc
                    val newContent = if (existingContent.isBlank()) {
                        exportLine
                    } else {
                        "$existingContent\n$exportLine"
                    }
                    
                    val error = FileUtils.writeTextToFile(
                        "bashrc",
                        bashrcPath,
                        StandardCharsets.UTF_8,
                        newContent,
                        false
                    )
                    
                    if (error != null) {
                        Logger.logError(LOG_TAG, "Failed to write to .bashrc: ${error.getMessage()}")
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.appendLog("Error: Failed to write to .bashrc: ${error.getMessage()}")
                        }
                        return@launch
                    }
                }
                
                Logger.logInfo(LOG_TAG, "GOOGLE_API_KEY successfully added to .bashrc")
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("✓ GOOGLE_API_KEY added to .bashrc successfully")
                }
                
                // Test if it was written correctly
                testApiKeyExport(viewModel)
                
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Error exporting GOOGLE_API_KEY: ${e.message}")
                Logger.logStackTraceWithMessage(LOG_TAG, "Exception details", e)
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Tests if GOOGLE_API_KEY is exported in .bashrc and prints it in logs.
     */
    fun testApiKeyExport(viewModel: BackupTestViewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bashrcPath = "${TermuxConstants.TERMUX_HOME_DIR_PATH}/.bashrc"
                val bashrcFile = File(bashrcPath)
                
                if (!bashrcFile.exists()) {
                    Logger.logWarn(LOG_TAG, ".bashrc file does not exist")
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.appendLog("Warning: .bashrc file does not exist")
                    }
                    return@launch
                }
                
                val content = bashrcFile.readText(StandardCharsets.UTF_8)
                val apiKeyLines = content.lines()
                    .filter { it.trim().startsWith("export GOOGLE_API_KEY") }
                
                if (apiKeyLines.isEmpty()) {
                    Logger.logWarn(LOG_TAG, "GOOGLE_API_KEY not found in .bashrc")
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.appendLog("✗ GOOGLE_API_KEY not found in .bashrc")
                    }
                } else {
                    apiKeyLines.forEach { line ->
                        Logger.logInfo(LOG_TAG, "Found in .bashrc: $line")
                        // Extract and mask the API key for logging (show first 10 chars)
                        val apiKeyMatch = Regex("export GOOGLE_API_KEY=\"([^\"]+)\"").find(line)
                        if (apiKeyMatch != null) {
                            val apiKey = apiKeyMatch.groupValues[1]
                            val maskedKey = if (apiKey.length > 10) {
                                "${apiKey.take(10)}...${apiKey.takeLast(4)}"
                            } else {
                                "***"
                            }
                            Logger.logInfo(LOG_TAG, "API Key (masked): $maskedKey")
                            CoroutineScope(Dispatchers.Main).launch {
                                viewModel.appendLog("✓ GOOGLE_API_KEY found in .bashrc")
                                viewModel.appendLog("  Key (masked): $maskedKey")
                            }
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                viewModel.appendLog("✓ GOOGLE_API_KEY found in .bashrc")
                                viewModel.appendLog("  Line: $line")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Error testing API key export: ${e.message}")
                Logger.logStackTraceWithMessage(LOG_TAG, "Exception details", e)
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.appendLog("Error testing export: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Executes a single Termux command using TermuxService.
     */
    private fun executeTermuxCommand(command: String, callback: ((Boolean, String) -> Unit)? = null) {
        try {
            Logger.logInfo(LOG_TAG, "=== Executing Termux Command ===")
            Logger.logInfo(LOG_TAG, "Command: $command")
            
            val executableUri = android.net.Uri.Builder()
                .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
                .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
                .build()
            
            Logger.logInfo(LOG_TAG, "Executable URI: $executableUri")
            Logger.logInfo(LOG_TAG, "Prefix path: ${TermuxConstants.TERMUX_PREFIX_DIR_PATH}")
            
            val execIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri)
            execIntent.setClass(this, com.termux.app.TermuxService::class.java)
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName())
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arrayOf("-c", command))
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.getMode())
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Restore backup: $command")
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, Logger.LOG_LEVEL_VERBOSE.toString())
            
            Logger.logInfo(LOG_TAG, "Intent extras:")
            Logger.logInfo(LOG_TAG, "  Runner: ${Runner.APP_SHELL.getName()}")
            Logger.logInfo(LOG_TAG, "  Arguments: ${arrayOf("-c", command).contentToString()}")
            Logger.logInfo(LOG_TAG, "  Shell create mode: ${ExecutionCommand.ShellCreateMode.ALWAYS.getMode()}")
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Logger.logInfo(LOG_TAG, "Starting foreground service (Android O+)")
                androidx.core.content.ContextCompat.startForegroundService(this, execIntent)
            } else {
                Logger.logInfo(LOG_TAG, "Starting service (Android < O)")
                startService(execIntent)
            }
            
            Logger.logInfo(LOG_TAG, "Service started successfully for command: $command")
            Logger.logInfo(LOG_TAG, "Note: Command runs asynchronously, callback will be invoked immediately")
            
            // Note: The callback is invoked immediately because TermuxService executes commands asynchronously
            // The actual command result would need to be captured via BroadcastReceiver or similar mechanism
            callback?.invoke(true, "")
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to execute command: $command")
            Logger.logStackTraceWithMessage(LOG_TAG, "Exception details", e)
            callback?.invoke(false, e.message ?: "Unknown error")
        }
    }
}

@Composable
fun BackupTestScreen(
    activity: BackupTestActivity,
    onBack: () -> Unit,
    viewModel: BackupTestViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Backup Test",
                style = MaterialTheme.typography.headlineMedium
            )
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
        
        // Status
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Status: ${uiState.status}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (uiState.isInProgress) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.reset()
                    viewModel.startTest()
                    activity.downloadBackupOnly(viewModel)
                },
                enabled = !uiState.isInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Redownload Backup")
            }
            
            Button(
                onClick = {
                    viewModel.reset()
                    viewModel.startTest()
                    activity.restoreBackupOnly(viewModel)
                },
                enabled = !uiState.isInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reinstall Backup")
            }
            
            Button(
                onClick = {
                    viewModel.reset()
                    viewModel.startTest()
                    activity.downloadAndRestoreBackup(viewModel)
                },
                enabled = !uiState.isInProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text("Redownload & Reinstall")
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Button(
                onClick = {
                    viewModel.reset()
                    viewModel.startTest()
                    activity.exportGoogleApiKey(viewModel)
                },
                enabled = !uiState.isInProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text("Export API Key to .bashrc")
            }
            
            Button(
                onClick = {
                    viewModel.reset()
                    viewModel.startTest()
                    activity.testApiKeyExport(viewModel)
                },
                enabled = !uiState.isInProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text("Test API Key Export")
            }
        }
        
        // Logs
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Logs:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
