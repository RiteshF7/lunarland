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
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int BUFFER_SIZE = 8192;

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

        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // Use a detailed custom message instead of the generic "Failed"
                // message from Errno so that the UI can show why the download
                // failed.
                String msg = "HTTP error " + responseCode + " when downloading bootstrap from " + url;
                return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
            }

            long contentLength = connection.getContentLengthLong();
            Logger.logInfo(LOG_TAG, "Content length: " + contentLength + " bytes");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = connection.getInputStream();
                 DigestInputStream digestStream = new DigestInputStream(inputStream, digest);
                 FileOutputStream outputStream = new FileOutputStream(targetFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long totalDownloaded = 0;
                int bytesRead;

                while ((bytesRead = digestStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalDownloaded += bytesRead;

                    if (progressCallback != null && contentLength > 0) {
                        progressCallback.onProgress(totalDownloaded, contentLength);
                    }
                }
            }

            String actualChecksum = bytesToHex(digest.digest());
            Logger.logInfo(LOG_TAG, "Downloaded bootstrap, SHA-256: " + actualChecksum);

            // Verify checksum if expected checksum is configured
            if (!expectedChecksum.isEmpty() && !actualChecksum.equalsIgnoreCase(expectedChecksum)) {
                targetFile.delete();
                String msg = "Checksum mismatch for bootstrap " + arch +
                    ": expected " + expectedChecksum + ", got " + actualChecksum;
                return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
            }

            Logger.logInfo(LOG_TAG, "Bootstrap downloaded successfully to " + targetFile.getAbsolutePath());

            // Persist the bootstrap version/tag so we can detect outdated cached bootstraps
            String version = BootstrapConfig.getBootstrapVersion();
            writeLocalBootstrapVersion(context, arch, version);
            return null;

        } catch (java.net.SocketTimeoutException e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "Timeout while downloading bootstrap: " + e.getMessage();
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } catch (java.net.UnknownHostException e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "Network error: Cannot reach GitHub. Check your internet connection.";
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } catch (java.io.IOException e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "IO error while downloading bootstrap: " + e.getMessage();
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } catch (Exception e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "Unexpected error while downloading bootstrap: " + e.getMessage();
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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

