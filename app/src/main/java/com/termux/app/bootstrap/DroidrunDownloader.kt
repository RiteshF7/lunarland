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
        
        Logger.logInfo(LOG_TAG, "Extracting droidrun zip to ${wheelsDir.absolutePath}")
        
        try {
            ZipInputStream(java.io.FileInputStream(zipFile)).use { zipInputStream ->
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
                        // Create parent directories if needed
                        targetFile.parentFile?.mkdirs()
                        
                        // Extract file
                        FileOutputStream(targetFile).use { outputStream ->
                            var bytesRead: Int
                            while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        }
                        
                        // Set executable permissions if it's in bin/ directory
                        if (entryName.startsWith("bin/") || entryName.endsWith(".so")) {
                            try {
                                targetFile.setExecutable(true, false)
                            } catch (e: Exception) {
                                Logger.logWarn(LOG_TAG, "Failed to set executable permissions for ${targetFile.absolutePath}: ${e.message}")
                            }
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
        
        onLogMessage?.invoke("Droidrun dependency installed successfully to ~/wheels")
        return null
    }
}

