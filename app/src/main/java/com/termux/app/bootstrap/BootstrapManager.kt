package com.termux.app.bootstrap

import android.content.Context
import com.termux.shared.errors.Error
import com.termux.shared.logger.Logger
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manager class for bootstrap operations.
 * Handles detection, download, and installation logic in a reusable, non-Android-specific way.
 */
object BootstrapManager {
    private const val LOG_TAG = "BootstrapManager"
    
    /**
     * Result of bootstrap detection check.
     */
    data class DetectionResult(
        val isInstalled: Boolean,
        val architecture: String?,
        val localBootstrapFile: File?
    )
    
    /**
     * Detects if bootstrap is installed and determines architecture.
     * @param context Android context
     * @return DetectionResult with installation status, architecture, and local file if available
     */
    fun detectBootstrap(context: Context): DetectionResult {
        Logger.logInfo(LOG_TAG, "Detecting bootstrap status...")
        
        val isInstalled = try {
            TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false) == null &&
            !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error checking bootstrap installation: ${e.message}")
            false
        }
        
        val architecture = try {
            BootstrapArchDetector.detectArch()
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error detecting architecture: ${e.message}")
            null
        }
        
        val localBootstrapFile = if (architecture != null) {
            try {
                BootstrapDownloader.getLocalBootstrapFile(context, architecture)
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Error getting local bootstrap file: ${e.message}")
                null
            }
        } else {
            null
        }
        
        Logger.logInfo(LOG_TAG, "Detection complete: installed=$isInstalled, arch=$architecture, localFile=${localBootstrapFile?.absolutePath}")
        
        return DetectionResult(
            isInstalled = isInstalled,
            architecture = architecture,
            localBootstrapFile = localBootstrapFile
        )
    }
    
    /**
     * Checks if a local bootstrap file exists and is valid.
     * @param context Android context
     * @param architecture Architecture string
     * @return File if valid local bootstrap exists, null otherwise
     */
    fun getLocalBootstrapFile(context: Context, architecture: String): File? {
        return try {
            val file = BootstrapDownloader.getLocalBootstrapFile(context, architecture)
            if (file != null && file.exists() && file.length() > 0) {
                file
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error getting local bootstrap file: ${e.message}")
            null
        }
    }
    
    /**
     * Downloads bootstrap for the given architecture.
     * @param context Android context
     * @param architecture Architecture string
     * @param progressCallback Optional callback for download progress
     * @return Error if download failed, null on success
     */
    suspend fun downloadBootstrap(
        context: Context,
        architecture: String,
        progressCallback: BootstrapDownloader.ProgressCallback?
    ): Error? {
        Logger.logInfo(LOG_TAG, "Starting bootstrap download for architecture: $architecture")
        // BootstrapDownloader.downloadBootstrap is a blocking Java method, call it from IO dispatcher
        return withContext(Dispatchers.IO) {
            BootstrapDownloader.downloadBootstrap(context, architecture, progressCallback)
        }
    }
    
    /**
     * Determines if bootstrap needs to be downloaded.
     * @param context Android context
     * @param architecture Architecture string
     * @return true if download is needed, false if valid local file exists
     */
    fun needsDownload(context: Context, architecture: String): Boolean {
        val localFile = getLocalBootstrapFile(context, architecture)
        if (localFile != null) {
            // Check if version matches
            val expectedVersion = BootstrapConfig.getBootstrapVersion()
            val localVersion = BootstrapDownloader.getLocalBootstrapVersion(context, architecture)
            
            val versionMatches = expectedVersion.isEmpty() ||
                (localVersion != null && expectedVersion == localVersion)
            
            if (!versionMatches) {
                Logger.logInfo(LOG_TAG, "Local bootstrap version mismatch, download needed")
                return true
            }
            
            Logger.logInfo(LOG_TAG, "Valid local bootstrap file exists, no download needed")
            return false
        }
        
        Logger.logInfo(LOG_TAG, "No local bootstrap file found, download needed")
        return true
    }
    
    /**
     * Deletes the installed bootstrap (prefix directory) to allow reinstallation.
     * @return Error if deletion failed, null on success
     */
    fun deleteInstalledBootstrap(): Error? {
        Logger.logInfo(LOG_TAG, "Deleting installed bootstrap prefix directory...")
        val error = FileUtils.deleteFile("termux prefix directory", TermuxConstants.TERMUX_PREFIX_DIR_PATH, true)
        if (error != null) {
            Logger.logError(LOG_TAG, "Failed to delete prefix directory: ${error.message}")
            return error
        }
        Logger.logInfo(LOG_TAG, "Successfully deleted prefix directory")
        return null
    }
}

