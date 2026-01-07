package com.termux.app.bootstrap

import android.content.Context
import com.termux.shared.errors.Error
import com.termux.shared.errors.Errno
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles downloading backup TAR.GZ files from GitHub releases using HttpURLConnection.
 * Similar approach to BootstrapDownloader.java
 */
object BackupDownloader {
    private const val LOG_TAG = "BackupDownloader"
    private const val CONNECT_TIMEOUT_MS = 30000
    private const val READ_TIMEOUT_MS = 1800000 // 30 minutes for large files
    private const val BUFFER_SIZE = 65536 // 64KB buffer
    private const val MAX_RETRIES = 5
    private const val PROGRESS_UPDATE_INTERVAL_MS = 2000L // Update every 2 seconds
    private const val PROGRESS_UPDATE_BYTES = 1024 * 1024L // Update every 1MB

    /**
     * Gets the download URL for backup file for the given architecture.
     */
    private fun getBackupUrl(arch: String): String {
        val version = BootstrapConfig.getBootstrapVersion()
        return "https://github.com/RiteshF7/termux-packages/releases/download/$version/termux-backup-$arch.tar.gz"
    }

    /**
     * Downloads backup TAR.GZ for the given architecture from GitHub.
     * @param context Android context
     * @param arch Architecture string (aarch64, arm, i686, x86_64)
     * @param progressCallback Optional callback for download progress (bytes downloaded, total bytes)
     * @return Error object if download failed, null on success
     */
    fun downloadBackup(
        context: Context,
        arch: String,
        progressCallback: ProgressCallback? = null
    ): Error? {
        val url = getBackupUrl(arch)
        // Download to files/home/ so Termux terminal can access it
        val homeDir = File(context.filesDir, "home")
        val fileName = "termux-backup-$arch.tar.gz"
        val targetFile = File(homeDir, fileName)

        Logger.logInfo(LOG_TAG, "Downloading backup for $arch from $url")

        // Create home directory if it doesn't exist
        val error = FileUtils.createDirectoryFile(homeDir.absolutePath)
        if (error != null) {
            return error
        }

        // Retry logic for network issues
        var lastError: Error? = null
        for (attempt in 0 until MAX_RETRIES) {
            if (attempt > 0) {
                Logger.logInfo(LOG_TAG, "Retry attempt $attempt of $MAX_RETRIES")
                try {
                    Thread.sleep(2000L * attempt) // Exponential backoff
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return Error(Errno.TYPE, Errno.ERRNO_FAILED.code, "Download interrupted")
                }
            }

            var connection: HttpURLConnection? = null
            try {
                val urlObj = URL(url)
                connection = urlObj.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("Connection", "keep-alive")
                connection.setRequestProperty("User-Agent", "Termux-BackupDownloader/1.0")
                connection.setRequestProperty("Accept-Encoding", "identity") // Disable compression for resume support
                connection.setChunkedStreamingMode(0)

                // Check if we have a partial download to resume from
                var startByte = 0L
                if (targetFile.exists()) {
                    startByte = targetFile.length()
                    if (startByte > 0) {
                        val mbPartial = startByte / (1024.0 * 1024.0)
                        Logger.logInfo(LOG_TAG, "Found partial download (%.2f MB), attempting to resume from byte %d".format(mbPartial, startByte))
                        // Reconnect with range request
                        connection.disconnect()
                        connection = urlObj.openConnection() as HttpURLConnection
                        connection.connectTimeout = CONNECT_TIMEOUT_MS
                        connection.readTimeout = READ_TIMEOUT_MS
                        connection.instanceFollowRedirects = true
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Connection", "keep-alive")
                        connection.setRequestProperty("User-Agent", "Termux-BackupDownloader/1.0")
                        connection.setRequestProperty("Accept-Encoding", "identity")
                        connection.setRequestProperty("Range", "bytes=$startByte-")
                        connection.setChunkedStreamingMode(0)
                    } else {
                        // File is empty, delete and start fresh
                        targetFile.delete()
                        startByte = 0
                    }
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    Logger.logInfo(LOG_TAG, "Server supports range requests, resuming from byte $startByte")
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    if (startByte > 0) {
                        Logger.logInfo(LOG_TAG, "Server doesn't support range requests, starting fresh")
                        targetFile.delete()
                        startByte = 0
                    }
                } else {
                    val msg = "HTTP error $responseCode when downloading backup from $url"
                    lastError = Error(Errno.TYPE, Errno.ERRNO_FAILED.code, msg)
                    connection.disconnect()
                    continue // Retry on HTTP errors
                }

                var contentLength = connection.contentLengthLong
                // If resuming, adjust content length to total file size
                if (startByte > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    contentLength = startByte + contentLength
                }
                Logger.logInfo(LOG_TAG, "Content length: $contentLength bytes" + 
                    if (startByte > 0) " (resuming from $startByte)" else "")

                connection.inputStream.use { inputStream ->
                    FileOutputStream(targetFile, startByte > 0).use { outputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var totalDownloaded = startByte
                        var bytesRead: Int
                        var lastLogTime = System.currentTimeMillis()
                        var lastLogBytes = totalDownloaded
                        var lastProgressUpdate = System.currentTimeMillis()
                        var bytesSinceLastUpdate = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            bytesSinceLastUpdate += bytesRead

                            // Flush periodically to ensure data is written to disk
                            if (bytesSinceLastUpdate >= 1024 * 1024) { // Every 1MB
                                outputStream.flush()
                                bytesSinceLastUpdate = 0
                            }

                            // Update progress more frequently for better UX
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MS || 
                                totalDownloaded - lastLogBytes >= PROGRESS_UPDATE_BYTES) {
                                
                                if (progressCallback != null && contentLength > 0) {
                                    progressCallback.onProgress(totalDownloaded, contentLength)
                                }
                                lastProgressUpdate = currentTime
                            }

                            // Log progress every 10 seconds or every 10MB
                            if (currentTime - lastLogTime > 10000 || totalDownloaded - lastLogBytes > 10 * 1024 * 1024) {
                                val percent = if (contentLength > 0) (totalDownloaded * 100.0 / contentLength) else 0.0
                                val mbDownloaded = totalDownloaded / (1024.0 * 1024.0)
                                val mbTotal = if (contentLength > 0) (contentLength / (1024.0 * 1024.0)) else 0.0
                                Logger.logInfo(LOG_TAG, "Downloaded %.2f MB / %.2f MB (%.1f%%)".format(mbDownloaded, mbTotal, percent))
                                lastLogTime = currentTime
                                lastLogBytes = totalDownloaded
                            }
                        }
                        
                        // Final flush
                        outputStream.flush()
                    }
                }

                Logger.logInfo(LOG_TAG, "Backup downloaded successfully to ${targetFile.absolutePath}")
                return null // Success!

            } catch (e: java.net.SocketTimeoutException) {
                Logger.logError(LOG_TAG, "Timeout on attempt ${attempt + 1}: ${e.message}")
                val partialSize = if (targetFile.exists()) targetFile.length() else 0
                Logger.logInfo(LOG_TAG, "Partial download size: $partialSize bytes")
                lastError = Error(Errno.TYPE, Errno.ERRNO_FAILED.code, 
                    "Timeout while downloading backup (downloaded $partialSize bytes). Will retry...")
                connection?.disconnect()
                // Keep partial file for resume on retry
                continue
            } catch (e: java.net.UnknownHostException) {
                Logger.logError(LOG_TAG, "Network error on attempt ${attempt + 1}: ${e.message}")
                if (targetFile.exists()) targetFile.delete()
                lastError = Error(Errno.TYPE, Errno.ERRNO_FAILED.code, 
                    "Network error: Cannot reach GitHub. Check your internet connection.")
                connection?.disconnect()
                continue
            } catch (e: java.io.IOException) {
                Logger.logError(LOG_TAG, "IO error on attempt ${attempt + 1}: ${e.message}")
                lastError = Error(Errno.TYPE, Errno.ERRNO_FAILED.code, 
                    "IO error while downloading backup: ${e.message}")
                connection?.disconnect()
                // Keep partial file for resume on retry
                continue
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Unexpected error on attempt ${attempt + 1}: ${e.message}")
                if (targetFile.exists()) targetFile.delete()
                lastError = Error(Errno.TYPE, Errno.ERRNO_FAILED.code, 
                    "Unexpected error while downloading backup: ${e.message}")
                connection?.disconnect()
                continue
            } finally {
                connection?.disconnect()
            }
        }
        
        // All retries failed
        if (targetFile.exists()) {
            targetFile.delete()
        }
        return lastError ?: Error(Errno.TYPE, Errno.ERRNO_FAILED.code, "Download failed after $MAX_RETRIES attempts")
    }

    /**
     * Gets the local backup file path if it exists and is valid.
     */
    fun getLocalBackupFile(context: Context, arch: String): File? {
        val homeDir = File(context.filesDir, "home")
        val backupFile = File(homeDir, "termux-backup-$arch.tar.gz")
        if (backupFile.exists() && backupFile.length() > 0) {
            return backupFile
        }
        return null
    }

    /**
     * Deletes the local backup file if it exists.
     */
    fun deleteLocalBackupFile(context: Context, arch: String): Boolean {
        val backupFile = getLocalBackupFile(context, arch)
        return if (backupFile != null && backupFile.exists()) {
            val deleted = backupFile.delete()
            Logger.logInfo(LOG_TAG, "Deleted local backup file: ${backupFile.absolutePath} -> $deleted")
            deleted
        } else {
            true // File doesn't exist, so it's "deleted"
        }
    }

    /**
     * Callback interface for download progress updates.
     */
    interface ProgressCallback {
        fun onProgress(downloaded: Long, total: Long)
    }
}
