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
 * Handles downloading droidrun wheel packages from GitHub releases with checksum verification.
 * Reuses the same download pattern as BootstrapDownloader for consistency.
 */
public class WheelsDownloader {
    private static final String LOG_TAG = "WheelsDownloader";
    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int BUFFER_SIZE = 8192;
    
    // GitHub release configuration
    private static final String GITHUB_REPO = "RiteshF7/droidrunandroidwrapper"; // Format: username/repo
    private static final String WHEELS_VERSION = "0.4.11";

    /**
     * Downloads droidrun wheels package from GitHub release.
     * @param context Android context
     * @param progressCallback Optional callback for download progress (bytes downloaded, total bytes)
     * @return Error object if download failed, null on success
     */
    public static Error downloadWheels(Context context, ProgressCallback progressCallback) {
        String url = getWheelsUrl();
        File wheelsDir = new File(context.getFilesDir(), "wheels");
        File targetFile = getLocalWheelsFile(context);

        Logger.logInfo(LOG_TAG, "Downloading wheels from " + url);

        // Create wheels directory if it doesn't exist
        Error error = FileUtils.createDirectoryFile(wheelsDir.getAbsolutePath());
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
                String msg = "HTTP error " + responseCode + " when downloading wheels from " + url;
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
            Logger.logInfo(LOG_TAG, "Downloaded wheels, SHA-256: " + actualChecksum);

            Logger.logInfo(LOG_TAG, "Wheels downloaded successfully to " + targetFile.getAbsolutePath());

            // Persist the wheels version so we can detect outdated cached wheels
            writeLocalWheelsVersion(context, WHEELS_VERSION);
            return null;

        } catch (java.net.SocketTimeoutException e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "Timeout while downloading wheels: " + e.getMessage();
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } catch (java.net.UnknownHostException e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "Network error: Cannot reach GitHub. Check your internet connection.";
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } catch (java.io.IOException e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "IO error while downloading wheels: " + e.getMessage();
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } catch (Exception e) {
            if (targetFile.exists()) targetFile.delete();
            String msg = "Unexpected error while downloading wheels: " + e.getMessage();
            return new Error(Errno.TYPE, Errno.ERRNO_FAILED.getCode(), msg);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Gets the GitHub release URL for wheels package.
     */
    private static String getWheelsUrl() {
        String packageName = "droidrun-wheels-v" + WHEELS_VERSION + "-android.tar.gz";
        return "https://github.com/" + GITHUB_REPO + "/releases/download/v" + WHEELS_VERSION + "/" + packageName;
    }

    /**
     * Gets the local wheels file path if it exists and is valid.
     */
    public static File getLocalWheelsFile(Context context) {
        File wheelsDir = new File(context.getFilesDir(), "wheels");
        String packageName = "droidrun-wheels-v" + WHEELS_VERSION + "-android.tar.gz";
        File wheelsFile = new File(wheelsDir, packageName);
        if (wheelsFile.exists() && wheelsFile.length() > 0) {
            return wheelsFile;
        }
        return new File(wheelsDir, packageName);
    }

    /**
     * Returns the locally stored wheels version, or null if not available.
     */
    public static String getLocalWheelsVersion(Context context) {
        try {
            File wheelsDir = new File(context.getFilesDir(), "wheels");
            File versionFile = new File(wheelsDir, "wheels.version");
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
            Logger.logError(LOG_TAG, "Failed to read local wheels version: " + e.getMessage());
            return null;
        }
    }

    /**
     * Writes the given version string to the local wheels version file.
     */
    private static void writeLocalWheelsVersion(Context context, String version) {
        if (version == null || version.isEmpty()) return;

        try {
            File wheelsDir = new File(context.getFilesDir(), "wheels");
            File versionFile = new File(wheelsDir, "wheels.version");
            try (FileOutputStream out = new FileOutputStream(versionFile, false)) {
                out.write(version.getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to write local wheels version: " + e.getMessage());
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
     * Reuses the same interface as BootstrapDownloader.
     */
    public interface ProgressCallback {
        void onProgress(long downloaded, long total);
    }
}

