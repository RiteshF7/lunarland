package com.termux.app.bootstrap;

import android.content.Context;
import com.termux.shared.errors.Error;
import com.termux.shared.errors.Errno;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;

/**
 * Handles downloading bootstrap ZIP files from GitHub with checksum verification.
 */
public class BootstrapDownloader {
    private static final String LOG_TAG = "BootstrapDownloader";
    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 1800000; // 30 minutes for 1GB+ downloads
    private static final int BUFFER_SIZE = 65536; // 64KB buffer for better performance
    private static final int MAX_RETRIES = 5; // More retries for large files
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 2000; // Update every 2 seconds
    private static final long PROGRESS_UPDATE_BYTES = 1024 * 1024; // Update every 1MB

    /**
     * Downloads bootstrap ZIP for the given architecture from GitHub.
     * @param context Android context
     * @param arch Architecture string (aarch64, arm, i686, x86_64)
     * @param progressCallback Optional callback for download progress (bytes downloaded, total bytes)
     * @return Error object if download failed, null on success
     */
    public static Error downloadBootstrap(Context context, String arch, ProgressCallback progressCallback) {
        String url = BootstrapConfig.getBootstrapUrl(arch);
        String expectedChecksum = BootstrapConfig.getExpectedChecksum(arch);
        File bootstrapDir = new File(context.getFilesDir(), "bootstraps");
        File targetFile = new File(bootstrapDir, "bootstrap-" + arch + ".zip");

        Logger.logInfo(LOG_TAG, "Downloading bootstrap for " + arch + " from " + url);

        // Create bootstrap directory if it doesn't exist
        Error error = FileUtils.createDirectoryFile(bootstrapDir.getAbsolutePath());
        if (error != null) {
            return error;
        }

        // Retry logic for network issues
        Error lastError = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                Logger.logInfo(LOG_TAG, "Retry attempt " + attempt + " of " + MAX_RETRIES);
                try {
                    Thread.sleep(2000 * attempt); // Exponential backoff
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), "Download interrupted");
                }
            }

            HttpURLConnection connection = null;
            try {
                URL urlObj = new URL(url);
                connection = (HttpURLConnection) urlObj.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Connection", "keep-alive");
                connection.setRequestProperty("User-Agent", "Termux-BootstrapDownloader/1.0");
                connection.setRequestProperty("Accept-Encoding", "identity"); // Disable compression for resume support
                connection.setChunkedStreamingMode(0); // Use default chunked mode

                // Check if we have a partial download to resume from
                // Try to resume on any attempt if partial file exists (not just retries)
                long startByte = 0;
                if (targetFile.exists()) {
                    startByte = targetFile.length();
                    if (startByte > 0) {
                        double mbPartial = startByte / (1024.0 * 1024.0);
                        Logger.logInfo(LOG_TAG, String.format("Found partial download (%.2f MB), attempting to resume from byte %d", 
                            mbPartial, startByte));
                        // Reconnect with range request
                        connection.disconnect();
                        connection = (HttpURLConnection) urlObj.openConnection();
                        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                        connection.setReadTimeout(READ_TIMEOUT_MS);
                        connection.setInstanceFollowRedirects(true);
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("Connection", "keep-alive");
                        connection.setRequestProperty("User-Agent", "Termux-BootstrapDownloader/1.0");
                        connection.setRequestProperty("Accept-Encoding", "identity"); // Disable compression for resume support
                        connection.setRequestProperty("Range", "bytes=" + startByte + "-");
                        connection.setChunkedStreamingMode(0); // Use default chunked mode
                    } else {
                        // File is empty, delete and start fresh
                        targetFile.delete();
                        startByte = 0;
                    }
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    // Server supports range requests, we'll append to existing file
                    Logger.logInfo(LOG_TAG, "Server supports range requests, resuming from byte " + startByte);
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Server doesn't support range requests or this is a fresh download
                    if (startByte > 0) {
                        Logger.logInfo(LOG_TAG, "Server doesn't support range requests, starting fresh");
                        targetFile.delete();
                        startByte = 0;
                    }
                } else {
                    // HTTP error
                    String msg = "HTTP error " + responseCode + " when downloading bootstrap from " + url;
                    lastError = new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
                    if (connection != null) connection.disconnect();
                    continue; // Retry on HTTP errors
                }

                long contentLength = connection.getContentLengthLong();
                // If resuming, adjust content length to total file size
                if (startByte > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    // Content-Length is the remaining bytes, add to startByte for total
                    contentLength = startByte + contentLength;
                }
                Logger.logInfo(LOG_TAG, "Content length: " + contentLength + " bytes" + 
                    (startByte > 0 ? " (resuming from " + startByte + ")" : ""));

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                
                // If resuming, hash the existing file first
                if (startByte > 0 && targetFile.exists()) {
                    Logger.logInfo(LOG_TAG, "Hashing existing file portion for checksum");
                    try (InputStream existingFile = new java.io.FileInputStream(targetFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = existingFile.read(buffer)) != -1) {
                            digest.update(buffer, 0, bytesRead);
                        }
                    }
                }
                
                try (InputStream inputStream = connection.getInputStream();
                     DigestInputStream digestStream = new DigestInputStream(inputStream, digest);
                     FileOutputStream outputStream = new FileOutputStream(targetFile, startByte > 0)) {

                    // With Range requests, server sends only remaining bytes, no need to skip

                    byte[] buffer = new byte[BUFFER_SIZE];
                    long totalDownloaded = startByte;
                    int bytesRead;
                    long lastLogTime = System.currentTimeMillis();
                    long lastLogBytes = totalDownloaded;
                    long lastProgressUpdate = System.currentTimeMillis();
                    long bytesSinceLastUpdate = 0;
                    long lastActivityTime = System.currentTimeMillis();

                    while ((bytesRead = digestStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalDownloaded += bytesRead;
                        bytesSinceLastUpdate += bytesRead;
                        lastActivityTime = System.currentTimeMillis();

                        // Flush periodically to ensure data is written to disk
                        if (bytesSinceLastUpdate >= 1024 * 1024) { // Every 1MB
                            outputStream.flush();
                            bytesSinceLastUpdate = 0;
                        }

                        // Update progress more frequently for better UX
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MS || 
                            totalDownloaded - lastLogBytes >= PROGRESS_UPDATE_BYTES) {
                            
                            if (progressCallback != null && contentLength > 0) {
                                progressCallback.onProgress(totalDownloaded, contentLength);
                            }
                            lastProgressUpdate = currentTime;
                        }

                        // Log progress every 10 seconds or every 10MB
                        if (currentTime - lastLogTime > 10000 || totalDownloaded - lastLogBytes > 10 * 1024 * 1024) {
                            double percent = contentLength > 0 ? (totalDownloaded * 100.0 / contentLength) : 0;
                            double mbDownloaded = totalDownloaded / (1024.0 * 1024.0);
                            double mbTotal = contentLength > 0 ? (contentLength / (1024.0 * 1024.0)) : 0;
                            Logger.logInfo(LOG_TAG, String.format("Downloaded %.2f MB / %.2f MB (%.1f%%)", 
                                mbDownloaded, mbTotal, percent));
                            lastLogTime = currentTime;
                            lastLogBytes = totalDownloaded;
                        }

                        // Note: Activity check removed as lastActivityTime is updated on each read,
                        // so this check would never trigger. The read timeout will handle connection issues.
                    }
                    
                    // Final flush
                    outputStream.flush();
                }

                String actualChecksum = bytesToHex(digest.digest());
                Logger.logInfo(LOG_TAG, "Downloaded bootstrap, SHA-256: " + actualChecksum);

                // Verify checksum if expected checksum is configured
                if (!expectedChecksum.isEmpty() && !actualChecksum.equalsIgnoreCase(expectedChecksum)) {
                    targetFile.delete();
                    String msg = "Checksum mismatch for bootstrap " + arch +
                        ": expected " + expectedChecksum + ", got " + actualChecksum;
                    lastError = new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
                    continue; // Retry on checksum mismatch
                }

                Logger.logInfo(LOG_TAG, "Bootstrap downloaded successfully to " + targetFile.getAbsolutePath());

                // Persist the bootstrap version/tag so we can detect outdated cached bootstraps
                String version = BootstrapConfig.getBootstrapVersion();
                writeLocalBootstrapVersion(context, arch, version);
                return null; // Success!

            } catch (java.net.SocketTimeoutException e) {
                Logger.logError(LOG_TAG, "Timeout on attempt " + (attempt + 1) + ": " + e.getMessage());
                long partialSize = targetFile.exists() ? targetFile.length() : 0;
                Logger.logInfo(LOG_TAG, "Partial download size: " + partialSize + " bytes");
                lastError = new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), 
                    "Timeout while downloading bootstrap (downloaded " + partialSize + " bytes). Will retry...");
                if (connection != null) connection.disconnect();
                // Keep partial file for resume on retry
                continue;
            } catch (java.net.UnknownHostException e) {
                Logger.logError(LOG_TAG, "Network error on attempt " + (attempt + 1) + ": " + e.getMessage());
                if (targetFile.exists()) targetFile.delete();
                lastError = new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), 
                    "Network error: Cannot reach GitHub. Check your internet connection.");
                if (connection != null) connection.disconnect();
                continue;
            } catch (java.io.IOException e) {
                Logger.logError(LOG_TAG, "IO error on attempt " + (attempt + 1) + ": " + e.getMessage());
                lastError = new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), 
                    "IO error while downloading bootstrap: " + e.getMessage());
                if (connection != null) connection.disconnect();
                // Keep partial file for resume on retry
                continue;
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Unexpected error on attempt " + (attempt + 1) + ": " + e.getMessage());
                if (targetFile.exists()) targetFile.delete();
                lastError = new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), 
                    "Unexpected error while downloading bootstrap: " + e.getMessage());
                if (connection != null) connection.disconnect();
                continue;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        
        // All retries failed
        if (targetFile.exists()) {
            targetFile.delete();
        }
        return lastError != null ? lastError : 
            new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), "Download failed after " + MAX_RETRIES + " attempts");
    }

    /**
     * Gets the local bootstrap file path if it exists and is valid.
     */
    public static File getLocalBootstrapFile(Context context, String arch) {
        File bootstrapDir = new File(context.getFilesDir(), "bootstraps");
        File bootstrapFile = new File(bootstrapDir, "bootstrap-" + arch + ".zip");
        if (bootstrapFile.exists() && bootstrapFile.length() > 0) {
            return bootstrapFile;
        }
        return null;
    }

    /**
     * Returns the locally stored bootstrap version for the given arch, or null if not available.
     * The version is stored in a sidecar file: bootstrap-{arch}.version
     */
    public static String getLocalBootstrapVersion(Context context, String arch) {
        try {
            File bootstrapDir = new File(context.getFilesDir(), "bootstraps");
            File versionFile = new File(bootstrapDir, "bootstrap-" + arch + ".version");
            if (!versionFile.exists() || versionFile.length() <= 0) {
                return null;
            }

            byte[] buffer = new byte[(int) versionFile.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(versionFile)) {
                int read = in.read(buffer);
                if (read <= 0) return null;
                return new String(buffer, 0, read, "UTF-8").trim();
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to read local bootstrap version: " + e.getMessage());
            return null;
        }
    }

    /**
     * Writes the given version string to the local bootstrap version file for the given arch.
     */
    public static void writeLocalBootstrapVersion(Context context, String arch, String version) {
        if (version == null || version.isEmpty()) return;

        try {
            File bootstrapDir = new File(context.getFilesDir(), "bootstraps");
            File versionFile = new File(bootstrapDir, "bootstrap-" + arch + ".version");
            try (FileOutputStream out = new FileOutputStream(versionFile, false)) {
                out.write(version.getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to write local bootstrap version: " + e.getMessage());
        }
    }

    /**
     * Verifies the checksum of a local bootstrap file.
     */
    public static boolean verifyChecksum(File bootstrapFile, String expectedChecksum) {
        if (expectedChecksum.isEmpty()) {
            return true; // Skip verification if checksum not configured
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = new java.io.FileInputStream(bootstrapFile);
                 DigestInputStream digestStream = new DigestInputStream(inputStream, digest)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (digestStream.read(buffer) != -1) {
                    // Read through entire file
                }
            }
            String actualChecksum = bytesToHex(digest.digest());
            return actualChecksum.equalsIgnoreCase(expectedChecksum);
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to verify checksum: " + e.getMessage());
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Callback interface for download progress updates.
     */
    public interface ProgressCallback {
        void onProgress(long downloaded, long total);
    }
}

