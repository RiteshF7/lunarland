package com.termux.app.bootstrap

import android.content.Context
import com.termux.shared.errors.Error
import com.termux.shared.errors.Errno
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import android.content.res.AssetManager

/**
 * Handles downloading droidrun dependency zip files from GitHub releases.
 */
object DroidrunDownloader {
    private const val LOG_TAG = "DroidrunDownloader"
    private const val REPO_OWNER = "RiteshF7"
    private const val REPO_NAME = "droidrunandroidwrapper"
    private const val CONNECT_TIMEOUT_MS = 30000
    private const val READ_TIMEOUT_MS = 60000
    private const val BUFFER_SIZE = 8192
    
    /**
     * Gets the GitHub release URL for droidrun dependency zip based on architecture.
     * Format: https://github.com/RiteshF7/droidrunandroidwrapper/releases/latest/download/{arch}_wheels.zip
     * Note: GitHub releases use /latest/download/ for the latest release assets
     * Actual filenames: arch64_wheels.zip, x86_wheels.zip
     */
    private fun getDroidrunUrl(arch: String): String {
        // Map architecture names to actual release filenames
        val fileName = when (arch) {
            "aarch64" -> "arch64_wheels.zip"
            "arm" -> "arch64_wheels.zip" // ARM might use the same as aarch64, adjust if needed
            "i686" -> "x86_wheels.zip"
            "x86_64" -> "x86_wheels.zip" // x86_64 might use x86, adjust if needed
            else -> "${arch}_wheels.zip"
        }
        return "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest/download/$fileName"
    }
    
    /**
     * Downloads droidrun dependency zip for the given architecture from GitHub.
     * @param context Android context
     * @param arch Architecture string (aarch64, arm, i686, x86_64)
     * @param progressCallback Optional callback for download progress
     * @param isCancelled Optional function to check if download should be cancelled
     * @return Error object if download failed, null on success
     */
    fun downloadDroidrun(
        context: Context,
        arch: String,
        progressCallback: ((Long, Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Error? {
        val url = getDroidrunUrl(arch)
        val tempDir = File(context.cacheDir, "droidrun")
        // Use the actual filename from the release
        val fileName = when (arch) {
            "aarch64" -> "arch64_wheels.zip"
            "arm" -> "arch64_wheels.zip"
            "i686" -> "x86_wheels.zip"
            "x86_64" -> "x86_wheels.zip"
            else -> "${arch}_wheels.zip"
        }
        val targetFile = File(tempDir, fileName)
        
        // Check if file already exists and is valid
        if (targetFile.exists() && targetFile.length() > 0) {
            Logger.logInfo(LOG_TAG, "Using existing droidrun zip file: ${targetFile.absolutePath} (size: ${targetFile.length()} bytes)")
            return null
        }
        
        Logger.logInfo(LOG_TAG, "Downloading droidrun dependency for $arch from $url")
        
        // Create temp directory if it doesn't exist
        val error = FileUtils.createDirectoryFile(tempDir.absolutePath)
        if (error != null) {
            return error
        }
        
        var connection: HttpURLConnection? = null
        try {
            val urlObj = URL(url)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val msg = "HTTP error $responseCode when downloading droidrun from $url"
                return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
            }
            
            val contentLength = connection.contentLengthLong
            Logger.logInfo(LOG_TAG, "Content length: $contentLength bytes")
            
            connection.inputStream.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalDownloaded = 0L
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation during download
                        if (isCancelled?.invoke() == true) {
                            connection.disconnect()
                            if (targetFile.exists()) targetFile.delete()
                            val msg = "Download cancelled by user"
                            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
                        }
                        
                        outputStream.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        
                        if (progressCallback != null && contentLength > 0) {
                            progressCallback(totalDownloaded, contentLength)
                        }
                    }
                }
            }
            
            Logger.logInfo(LOG_TAG, "Droidrun dependency downloaded successfully to ${targetFile.absolutePath}")
            return null
            
        } catch (e: java.net.SocketTimeoutException) {
            if (targetFile.exists()) targetFile.delete()
            val msg = "Timeout while downloading droidrun: ${e.message}"
            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
        } catch (e: java.net.UnknownHostException) {
            if (targetFile.exists()) targetFile.delete()
            val msg = "Network error: Cannot reach GitHub. Check your internet connection."
            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
        } catch (e: java.io.IOException) {
            if (targetFile.exists()) targetFile.delete()
            val msg = "IO error while downloading droidrun: ${e.message}"
            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
            val msg = "Unexpected error while downloading droidrun: ${e.message}"
            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Extracts the downloaded droidrun zip to ~/wheels directory.
     * @param context Android context
     * @param arch Architecture string
     * @return Error if extraction failed, null on success
     */
    fun extractDroidrunToWheels(
        context: Context,
        arch: String
    ): Error? {
        val tempDir = File(context.cacheDir, "droidrun")
        // Use the actual filename from the release
        val fileName = when (arch) {
            "aarch64" -> "arch64_wheels.zip"
            "arm" -> "arch64_wheels.zip"
            "i686" -> "x86_wheels.zip"
            "x86_64" -> "x86_wheels.zip"
            else -> "${arch}_wheels.zip"
        }
        val zipFile = File(tempDir, fileName)
        
        if (!zipFile.exists()) {
            val msg = "Droidrun zip file not found: ${zipFile.absolutePath}"
            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
        }
        
        // Create ~/wheels directory (which is $HOME/wheels = $PREFIX/home/wheels)
        val wheelsDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, "wheels")
        val error = FileUtils.createDirectoryFile(wheelsDir.absolutePath)
        if (error != null) {
            return error
        }
        
        // Make wheels directory readable and executable
        try {
            wheelsDir.setReadable(true, false)
            wheelsDir.setExecutable(true, false)
            wheelsDir.setWritable(true, false)
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Failed to set permissions for wheels directory: ${e.message}")
        }
        
        Logger.logInfo(LOG_TAG, "Extracting droidrun zip to ${wheelsDir.absolutePath}")
        
        // Delete any existing corrupted wheel files (files < 1KB are likely corrupted)
        if (wheelsDir.exists() && wheelsDir.isDirectory) {
            val existingWheels = wheelsDir.listFiles { _, name -> name.endsWith(".whl") }
            existingWheels?.forEach { wheelFile ->
                if (wheelFile.length() < 1024) {
                    Logger.logWarn(LOG_TAG, "Deleting corrupted wheel file: ${wheelFile.name} (${wheelFile.length()} bytes)")
                    wheelFile.delete()
                }
            }
        }
        
        try {
            ZipInputStream(java.io.FileInputStream(zipFile).buffered()).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                val buffer = ByteArray(BUFFER_SIZE)
                
                while (entry != null) {
                    val entryName = entry.name
                    val targetFile = File(wheelsDir, entryName)
                    
                    if (entry.isDirectory) {
                        // Create directory
                        if (!targetFile.exists()) {
                            targetFile.mkdirs()
                        }
                    } else {
                        // Delete existing file if it exists (to ensure clean extraction)
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        
                        // Create parent directories if needed
                        targetFile.parentFile?.mkdirs()
                        
                        // Extract file
                        var totalBytesWritten = 0L
                        FileOutputStream(targetFile).use { outputStream ->
                            var bytesRead: Int
                            while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesWritten += bytesRead
                            }
                            outputStream.flush()
                        }
                        
                        // Verify file was written correctly
                        if (entry.size > 0 && targetFile.length() != entry.size) {
                            val errorMsg = "File size mismatch for ${targetFile.absolutePath}: expected ${entry.size}, got ${targetFile.length()}"
                            Logger.logError(LOG_TAG, errorMsg)
                            targetFile.delete() // Delete corrupted file
                            throw java.io.IOException(errorMsg)
                        }
                        
                        if (entryName.endsWith(".whl") && targetFile.length() < 1024) {
                            val errorMsg = "Extracted wheel file is too small: ${targetFile.name} (${targetFile.length()} bytes) - likely corrupted"
                            Logger.logError(LOG_TAG, errorMsg)
                            targetFile.delete() // Delete corrupted file
                            throw java.io.IOException(errorMsg)
                        }
                        
                        // Set readable permissions for all extracted files
                        try {
                            targetFile.setReadable(true, false)
                            targetFile.setWritable(true, false)
                        } catch (e: Exception) {
                            Logger.logWarn(LOG_TAG, "Failed to set permissions for ${targetFile.absolutePath}: ${e.message}")
                        }
                        
                        // Set executable permissions if it's in bin/ directory or is a .whl file
                        if (entryName.startsWith("bin/") || entryName.endsWith(".so") || entryName.endsWith(".whl")) {
                            try {
                                targetFile.setExecutable(true, false)
                            } catch (e: Exception) {
                                Logger.logWarn(LOG_TAG, "Failed to set executable permissions for ${targetFile.absolutePath}: ${e.message}")
                            }
                        }
                        
                        // Log wheel file extraction
                        if (entryName.endsWith(".whl")) {
                            Logger.logInfo(LOG_TAG, "Extracted wheel: ${targetFile.name} (${targetFile.length()} bytes)")
                        }
                    }
                    
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
            
            Logger.logInfo(LOG_TAG, "Successfully extracted droidrun to ${wheelsDir.absolutePath}")
            
            // Clean up zip file after extraction
            zipFile.delete()
            
            return null
            
        } catch (e: Exception) {
            val msg = "Error extracting droidrun zip: ${e.message}"
            Logger.logStackTraceWithMessage(LOG_TAG, msg, e)
            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
        }
    }
    
    /**
     * Downloads and extracts droidrun dependency in one operation.
     */
    fun downloadAndExtractDroidrun(
        context: Context,
        arch: String,
        progressCallback: ((Long, Long) -> Unit)? = null,
        onLogMessage: ((String) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Error? {
        var skipDownload = false
        
        // Check if wheels directory already has valid wheel files (files > 1KB are likely valid)
        val wheelsDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, "wheels")
        if (wheelsDir.exists() && wheelsDir.isDirectory) {
            val existingWheels = wheelsDir.listFiles { _, name -> name.endsWith(".whl") }
            val validWheels = existingWheels?.filter { it.length() >= 1024 } // At least 1KB
            if (validWheels != null && validWheels.isNotEmpty()) {
                onLogMessage?.invoke("Found ${validWheels.size} existing valid wheel files, skipping download")
                Logger.logInfo(LOG_TAG, "Skipping download - ${validWheels.size} valid wheels already exist")
                skipDownload = true
            } else if (existingWheels != null && existingWheels.isNotEmpty()) {
                // Found wheels but they're all corrupted, delete them and re-download
                onLogMessage?.invoke("Found ${existingWheels.size} corrupted wheel files, will re-download")
                Logger.logWarn(LOG_TAG, "Found ${existingWheels.size} corrupted wheels, deleting and re-downloading")
                existingWheels.forEach { it.delete() }
            }
        }
        
        if (!skipDownload) {
            onLogMessage?.invoke("Downloading droidrun dependency for $arch...")
            val downloadError = downloadDroidrun(context, arch, progressCallback, isCancelled)
            if (downloadError != null) {
                return downloadError
            }
            
            onLogMessage?.invoke("Extracting droidrun dependency to ~/wheels...")
            val extractError = extractDroidrunToWheels(context, arch)
            if (extractError != null) {
                return extractError
            }
            
            onLogMessage?.invoke("Droidrun dependency downloaded and extracted successfully to ~/wheels")
        } else {
            onLogMessage?.invoke("Using existing wheel files in ~/wheels")
        }
        
        // Always execute installation script, even if wheels already existed
        onLogMessage?.invoke("Running droidrun installation script...")
        val scriptError = executeInstallationScript(context, onLogMessage, isCancelled)
        if (scriptError != null) {
            return scriptError
        }
        
        return null
    }
    
    /**
     * Copies the installation script from assets to a location where it can be executed.
     */
    private fun copyScriptFromAssets(context: Context): File? {
        return try {
            val scriptFile = File(context.filesDir, "install-droidrun.sh")
            val assetManager: AssetManager = context.assets
            
            assetManager.open("install-droidrun.sh").use { inputStream ->
                FileOutputStream(scriptFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // Make script executable
            scriptFile.setExecutable(true, false)
            Logger.logInfo(LOG_TAG, "Script copied to ${scriptFile.absolutePath}")
            scriptFile
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to copy script from assets: ${e.message}")
            null
        }
    }
    
    /**
     * Executes the installation script from assets.
     */
    fun executeInstallationScript(
        context: Context,
        onLogMessage: ((String) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Error? {
        try {
            val scriptFile = copyScriptFromAssets(context)
            if (scriptFile == null || !scriptFile.exists()) {
                val msg = "Failed to copy installation script from assets"
                return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
            }
            
            onLogMessage?.invoke("Executing installation script: ${scriptFile.absolutePath}")
            
            // Execute the script using bash
            val processBuilder = ProcessBuilder(
                "/data/data/com.termux/files/usr/bin/bash",
                scriptFile.absolutePath
            )
            
            // Set environment variables
            val env = processBuilder.environment()
            env["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
            env["PREFIX"] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
            
            processBuilder.directory(File(TermuxConstants.TERMUX_HOME_DIR_PATH))
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            
            // Read output in a separate thread
            val outputThread = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        onLogMessage?.invoke(line)
                        Logger.logInfo(LOG_TAG, "Script output: $line")
                    }
                } catch (e: Exception) {
                    Logger.logError(LOG_TAG, "Error reading script output: ${e.message}")
                }
            }
            outputThread.start()
            
            // Wait for process with cancellation check
            while (process.isAlive) {
                if (isCancelled?.invoke() == true) {
                    process.destroy()
                    onLogMessage?.invoke("Installation script cancelled")
                    return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, "Script execution cancelled")
                }
                Thread.sleep(100)
            }
            
            outputThread.join(1000)
            
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                onLogMessage?.invoke("Installation script completed successfully")
                Logger.logInfo(LOG_TAG, "Installation script completed with exit code: $exitCode")
                return null
            } else {
                val msg = "Installation script failed with exit code: $exitCode"
                Logger.logError(LOG_TAG, msg)
                onLogMessage?.invoke("ERROR: $msg")
                return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
            }
            
        } catch (e: Exception) {
            val msg = "Error executing installation script: ${e.message}"
            Logger.logStackTraceWithMessage(LOG_TAG, msg, e)
            onLogMessage?.invoke("ERROR: $msg")
            return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
        }
    }
}

