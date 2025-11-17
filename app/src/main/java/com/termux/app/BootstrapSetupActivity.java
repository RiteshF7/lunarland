package com.termux.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.bootstrap.BootstrapArchDetector;
import com.termux.app.bootstrap.BootstrapConfig;
import com.termux.app.bootstrap.BootstrapDownloader;
import com.termux.shared.errors.Error;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.file.TermuxFileUtils;

/**
 * Activity responsible for downloading and installing bootstrap packages from GitHub.
 * Shows progress, logs, and handles errors with retry functionality.
 */
public class BootstrapSetupActivity extends AppCompatActivity {
    private static final String LOG_TAG = "BootstrapSetupActivity";

    private ProgressBar progressBar;
    private TextView progressText;
    private TextView logText;
    private TextView errorText;
    private Button retryButton;
    private Button homeButton;
    private ScrollView logContainer;

    // Step buttons
    private Button stepCheckBootstrap;
    private Button stepSkipCheckBootstrap;
    private Button stepRerunCheckBootstrap;
    private Button stepDetectArch;
    private Button stepSkipDetectArch;
    private Button stepRerunDetectArch;
    private Button stepCheckLocal;
    private Button stepSkipCheckLocal;
    private Button stepRerunCheckLocal;
    private Button stepDownload;
    private Button stepSkipDownload;
    private Button stepRerunDownload;
    private Button stepInstall;
    private Button stepSkipInstall;
    private Button stepRerunInstall;
    private Button stepDownloadLunar;
    private Button stepSkipDownloadLunar;
    private Button stepRerunDownloadLunar;

    private java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean isSetupInProgress = false;

    // Throttling state for progress/log updates to keep UI smooth during download.
    private volatile int lastDownloadProgressPercent = 0;
    private volatile long lastDownloadLogUpdateTimeMs = 0L;

    // State tracking
    private String detectedArch = null;
    private java.io.File localBootstrapFile = null;
    private boolean bootstrapAlreadyInstalled = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bootstrap_setup);

        progressBar = findViewById(R.id.bootstrap_setup_progress);
        progressText = findViewById(R.id.bootstrap_setup_progress_text);
        logText = findViewById(R.id.bootstrap_setup_log);
        errorText = findViewById(R.id.bootstrap_setup_error);
        retryButton = findViewById(R.id.bootstrap_setup_retry_button);
        homeButton = findViewById(R.id.bootstrap_setup_home_button);
        logContainer = findViewById(R.id.bootstrap_setup_log_container);

        // Initialize step buttons
        stepCheckBootstrap = findViewById(R.id.step_check_bootstrap);
        stepSkipCheckBootstrap = findViewById(R.id.step_skip_check_bootstrap);
        stepRerunCheckBootstrap = findViewById(R.id.step_rerun_check_bootstrap);
        stepDetectArch = findViewById(R.id.step_detect_arch);
        stepSkipDetectArch = findViewById(R.id.step_skip_detect_arch);
        stepRerunDetectArch = findViewById(R.id.step_rerun_detect_arch);
        stepCheckLocal = findViewById(R.id.step_check_local);
        stepSkipCheckLocal = findViewById(R.id.step_skip_check_local);
        stepRerunCheckLocal = findViewById(R.id.step_rerun_check_local);
        stepDownload = findViewById(R.id.step_download);
        stepSkipDownload = findViewById(R.id.step_skip_download);
        stepRerunDownload = findViewById(R.id.step_rerun_download);
        stepInstall = findViewById(R.id.step_install);
        stepSkipInstall = findViewById(R.id.step_skip_install);
        stepRerunInstall = findViewById(R.id.step_rerun_install);
        stepDownloadLunar = findViewById(R.id.step_download_lunar);
        stepSkipDownloadLunar = findViewById(R.id.step_skip_download_lunar);
        stepRerunDownloadLunar = findViewById(R.id.step_rerun_download_lunar);

        // Retry button click handler
        retryButton.setOnClickListener(v -> {
            errorText.setText("");
            updateErrorVisibility(false);
        });

        // Home button click handler
        homeButton.setOnClickListener(v -> navigateToHome());

        // Step button click handlers
        stepCheckBootstrap.setOnClickListener(v -> performCheckBootstrap());
        stepSkipCheckBootstrap.setOnClickListener(v -> skipCheckBootstrap());
        stepRerunCheckBootstrap.setOnClickListener(v -> rerunCheckBootstrap());
        stepDetectArch.setOnClickListener(v -> performDetectArch());
        stepSkipDetectArch.setOnClickListener(v -> skipDetectArch());
        stepRerunDetectArch.setOnClickListener(v -> rerunDetectArch());
        stepCheckLocal.setOnClickListener(v -> performCheckLocal());
        stepSkipCheckLocal.setOnClickListener(v -> skipCheckLocal());
        stepRerunCheckLocal.setOnClickListener(v -> rerunCheckLocal());
        stepDownload.setOnClickListener(v -> performDownload());
        stepSkipDownload.setOnClickListener(v -> skipDownload());
        stepRerunDownload.setOnClickListener(v -> rerunDownload());
        stepInstall.setOnClickListener(v -> performInstall());
        stepSkipInstall.setOnClickListener(v -> skipInstall());
        stepRerunInstall.setOnClickListener(v -> rerunInstall());
        stepDownloadLunar.setOnClickListener(v -> performDownloadLunar());
        stepSkipDownloadLunar.setOnClickListener(v -> skipDownloadLunar());
        stepRerunDownloadLunar.setOnClickListener(v -> rerunDownloadLunar());

        appendLog("Select steps to perform or skip. Setup will not start automatically.");
    }

    /**
     * Triggers bootstrap installation using TermuxInstaller.
     */
    private void installBootstrapWithLunarAgentSetup() {
        // Update UI to show installation in progress
        logText.append("\nExtracting bootstrap archive...");
        progressBar.setProgress(92);
        progressText.setText("92%");
        logContainer.post(() -> logContainer.fullScroll(View.FOCUS_DOWN));
        
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
            // Installation complete
            runOnUiThread(() -> {
                progressBar.setProgress(100);
                progressText.setText("100%");
                appendLog("Bootstrap installed successfully.");
                logContainer.post(() -> logContainer.fullScroll(View.FOCUS_DOWN));
                isSetupInProgress = false;
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void performCheckBootstrap() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Checking if bootstrap is needed...");
        disableButton(stepCheckBootstrap);

        executor.execute(() -> {
            try {
                if (TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false) == null &&
                    !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                    Logger.logInfo(LOG_TAG, "Bootstrap already installed");
                    appendLog("Bootstrap already installed.");
                    bootstrapAlreadyInstalled = true;
                    runOnUiThread(() -> {
                        stepCheckBootstrap.setText("1. Check Bootstrap ✓");
                        showRerunButton(stepCheckBootstrap, stepSkipCheckBootstrap, stepRerunCheckBootstrap);
                    });
                } else {
                    appendLog("Bootstrap not found, setup needed.");
                    bootstrapAlreadyInstalled = false;
                    runOnUiThread(() -> {
                        stepCheckBootstrap.setText("1. Check Bootstrap ✓");
                        showRerunButton(stepCheckBootstrap, stepSkipCheckBootstrap, stepRerunCheckBootstrap);
                    });
                }
            } catch (Exception e) {
                String errorMsg = "Check failed: " + e.getMessage();
                Logger.logStackTraceWithMessage(LOG_TAG, "Check failed", e);
                showError(errorMsg);
                runOnUiThread(() -> enableButton(stepCheckBootstrap));
            } finally {
                isSetupInProgress = false;
            }
        });
    }

    private void skipCheckBootstrap() {
        appendLog("Skipped: Check Bootstrap");
        stepCheckBootstrap.setText("1. Check Bootstrap (Skipped)");
        showRerunButton(stepCheckBootstrap, stepSkipCheckBootstrap, stepRerunCheckBootstrap);
    }

    private void rerunCheckBootstrap() {
        appendLog("Rerunning: Check Bootstrap");
        stepCheckBootstrap.setText("1. Check Bootstrap");
        hideRerunButton(stepCheckBootstrap, stepSkipCheckBootstrap, stepRerunCheckBootstrap);
        stepCheckBootstrap.setEnabled(true);
        stepSkipCheckBootstrap.setEnabled(true);
        performCheckBootstrap();
    }

    private void performDetectArch() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Detecting architecture...");
        disableButton(stepDetectArch);

        executor.execute(() -> {
            try {
                detectedArch = BootstrapArchDetector.detectArch();
                appendLog("Detected architecture: " + detectedArch);
                updateProgressPercent(5);
                runOnUiThread(() -> {
                    stepDetectArch.setText("2. Detect Architecture ✓");
                    showRerunButton(stepDetectArch, stepSkipDetectArch, stepRerunDetectArch);
                });
            } catch (Exception e) {
                String errorMsg = "Architecture detection failed: " + e.getMessage();
                Logger.logStackTraceWithMessage(LOG_TAG, "Architecture detection failed", e);
                showError(errorMsg);
                runOnUiThread(() -> enableButton(stepDetectArch));
            } finally {
                isSetupInProgress = false;
            }
        });
    }

    private void skipDetectArch() {
        appendLog("Skipped: Detect Architecture");
        stepDetectArch.setText("2. Detect Architecture (Skipped)");
        showRerunButton(stepDetectArch, stepSkipDetectArch, stepRerunDetectArch);
    }

    private void rerunDetectArch() {
        appendLog("Rerunning: Detect Architecture");
        detectedArch = null;
        stepDetectArch.setText("2. Detect Architecture");
        hideRerunButton(stepDetectArch, stepSkipDetectArch, stepRerunDetectArch);
        stepDetectArch.setEnabled(true);
        stepSkipDetectArch.setEnabled(true);
        performDetectArch();
    }

    private void performCheckLocal() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        if (detectedArch == null) {
            showError("Please detect architecture first or skip that step");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Checking for local bootstrap file...");
        disableButton(stepCheckLocal);

        executor.execute(() -> {
            try {
                localBootstrapFile = BootstrapDownloader.getLocalBootstrapFile(getApplication(), detectedArch);
                String expectedChecksum = BootstrapConfig.getExpectedChecksum(detectedArch);

                if (localBootstrapFile != null) {
                    if (!expectedChecksum.isEmpty() &&
                        !BootstrapDownloader.verifyChecksum(localBootstrapFile, expectedChecksum)) {
                        appendLog("Local bootstrap checksum invalid, will need to re-download...");
                        localBootstrapFile.delete();
                        localBootstrapFile = null;
                    } else {
                        appendLog("Found valid cached bootstrap file");
                        updateProgressPercent(20);
                    }
                } else {
                    appendLog("No local bootstrap file found");
                }

                runOnUiThread(() -> {
                    stepCheckLocal.setText("3. Check Local File ✓");
                    showRerunButton(stepCheckLocal, stepSkipCheckLocal, stepRerunCheckLocal);
                });
            } catch (Exception e) {
                String errorMsg = "Check local file failed: " + e.getMessage();
                Logger.logStackTraceWithMessage(LOG_TAG, "Check local file failed", e);
                showError(errorMsg);
                runOnUiThread(() -> enableButton(stepCheckLocal));
            } finally {
                isSetupInProgress = false;
            }
        });
    }

    private void skipCheckLocal() {
        appendLog("Skipped: Check Local File");
        stepCheckLocal.setText("3. Check Local File (Skipped)");
        showRerunButton(stepCheckLocal, stepSkipCheckLocal, stepRerunCheckLocal);
    }

    private void rerunCheckLocal() {
        appendLog("Rerunning: Check Local File");
        localBootstrapFile = null;
        stepCheckLocal.setText("3. Check Local File");
        hideRerunButton(stepCheckLocal, stepSkipCheckLocal, stepRerunCheckLocal);
        stepCheckLocal.setEnabled(true);
        stepSkipCheckLocal.setEnabled(true);
        performCheckLocal();
    }

    private void performDownload() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        if (detectedArch == null) {
            showError("Please detect architecture first or skip that step");
            return;
        }

        if (localBootstrapFile != null && localBootstrapFile.exists()) {
            appendLog("Local bootstrap file already exists, skipping download");
            skipDownload();
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Downloading bootstrap from GitHub...");
        updateProgressPercent(10);
        lastDownloadProgressPercent = 10;
        lastDownloadLogUpdateTimeMs = System.currentTimeMillis();
        disableButton(stepDownload);

        executor.execute(() -> {
            try {
                Error downloadError = BootstrapDownloader.downloadBootstrap(
                    getApplication(),
                    detectedArch,
                    (downloaded, total) -> {
                        if (total > 0) {
                            int percent = 10 + (int) ((downloaded * 70) / total);

                            if (percent != lastDownloadProgressPercent) {
                                lastDownloadProgressPercent = percent;
                                updateProgressPercent(percent);
                            }

                            long now = System.currentTimeMillis();
                            if (now - lastDownloadLogUpdateTimeMs >= 500) {
                                lastDownloadLogUpdateTimeMs = now;
                                appendLog(String.format(
                                    "Downloading: %d / %d bytes (%.1f%%)",
                                    downloaded, total, (downloaded * 100.0 / total)));
                            }
                        }
                    }
                );

                if (downloadError != null) {
                    String errorMsg = "Download failed: " + downloadError.getMessage();
                    Logger.logError(LOG_TAG, errorMsg);
                    showError(errorMsg);
                    runOnUiThread(() -> enableButton(stepDownload));
                    return;
                }

                appendLog("Download completed");
                updateProgressPercent(80);

                // Verify bootstrap file exists
                localBootstrapFile = BootstrapDownloader.getLocalBootstrapFile(getApplication(), detectedArch);
                if (localBootstrapFile == null || !localBootstrapFile.exists() || localBootstrapFile.length() == 0) {
                    String errorMsg = "Bootstrap file not found or empty after download";
                    Logger.logError(LOG_TAG, errorMsg);
                    showError(errorMsg);
                    runOnUiThread(() -> enableButton(stepDownload));
                    return;
                }

                runOnUiThread(() -> {
                    stepDownload.setText("4. Download Bootstrap ✓");
                    showRerunButton(stepDownload, stepSkipDownload, stepRerunDownload);
                });
            } catch (Exception e) {
                String errorMsg = "Download failed: " + e.getMessage();
                Logger.logStackTraceWithMessage(LOG_TAG, "Download failed", e);
                showError(errorMsg);
                runOnUiThread(() -> enableButton(stepDownload));
            } finally {
                isSetupInProgress = false;
            }
        });
    }

    private void skipDownload() {
        appendLog("Skipped: Download Bootstrap");
        stepDownload.setText("4. Download Bootstrap (Skipped)");
        showRerunButton(stepDownload, stepSkipDownload, stepRerunDownload);
    }

    private void rerunDownload() {
        appendLog("Rerunning: Download Bootstrap");
        localBootstrapFile = null;
        stepDownload.setText("4. Download Bootstrap");
        hideRerunButton(stepDownload, stepSkipDownload, stepRerunDownload);
        stepDownload.setEnabled(true);
        stepSkipDownload.setEnabled(true);
        performDownload();
    }

    private void performInstall() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        if (bootstrapAlreadyInstalled) {
            appendLog("Bootstrap already installed, skipping installation");
            skipInstall();
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Starting bootstrap installation...");
        updateProgressPercent(90);
        disableButton(stepInstall);

        runOnUiThread(() -> {
            installBootstrapWithLunarAgentSetup();
            stepInstall.setText("5. Install Bootstrap ✓");
            showRerunButton(stepInstall, stepSkipInstall, stepRerunInstall);
        });
    }

    private void skipInstall() {
        appendLog("Skipped: Install Bootstrap");
        stepInstall.setText("5. Install Bootstrap (Skipped)");
        showRerunButton(stepInstall, stepSkipInstall, stepRerunInstall);
    }

    private void rerunInstall() {
        appendLog("Rerunning: Install Bootstrap");
        bootstrapAlreadyInstalled = false;
        stepInstall.setText("5. Install Bootstrap");
        hideRerunButton(stepInstall, stepSkipInstall, stepRerunInstall);
        stepInstall.setEnabled(true);
        stepSkipInstall.setEnabled(true);
        performInstall();
    }

    private void performDownloadLunar() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Downloading lunar-adb-agent...");
        disableButton(stepDownloadLunar);

        executor.execute(() -> {
            try {
                java.io.File lunarDir = new java.io.File(
                    TermuxConstants.TERMUX_HOME_DIR_PATH, "lunar-adb-agent");
                if (!lunarDir.exists() && !lunarDir.mkdirs()) {
                    Logger.logError(LOG_TAG,
                        "Failed to create lunar-adb-agent directory at " + lunarDir.getAbsolutePath());
                    showError("Failed to create lunar-adb-agent directory");
                    runOnUiThread(() -> enableButton(stepDownloadLunar));
                    return;
                }
                downloadLunarAdbAgent(lunarDir);
                runOnUiThread(() -> {
                    stepDownloadLunar.setText("6. Download Lunar Agent ✓");
                    showRerunButton(stepDownloadLunar, stepSkipDownloadLunar, stepRerunDownloadLunar);
                });
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG,
                    "Failed to set up lunar-adb-agent", e);
                showError("Failed to download lunar-adb-agent: " + e.getMessage());
                runOnUiThread(() -> enableButton(stepDownloadLunar));
            } finally {
                isSetupInProgress = false;
            }
        });
    }

    private void skipDownloadLunar() {
        appendLog("Skipped: Download Lunar Agent");
        stepDownloadLunar.setText("6. Download Lunar Agent (Skipped)");
        showRerunButton(stepDownloadLunar, stepSkipDownloadLunar, stepRerunDownloadLunar);
    }

    private void rerunDownloadLunar() {
        appendLog("Rerunning: Download Lunar Agent");
        stepDownloadLunar.setText("6. Download Lunar Agent");
        hideRerunButton(stepDownloadLunar, stepSkipDownloadLunar, stepRerunDownloadLunar);
        stepDownloadLunar.setEnabled(true);
        stepSkipDownloadLunar.setEnabled(true);
        performDownloadLunar();
    }

    private void disableButton(Button button) {
        runOnUiThread(() -> button.setEnabled(false));
    }

    private void enableButton(Button button) {
        runOnUiThread(() -> button.setEnabled(true));
    }

    private void showRerunButton(Button runButton, Button skipButton, Button rerunButton) {
        runOnUiThread(() -> {
            runButton.setVisibility(View.GONE);
            skipButton.setVisibility(View.GONE);
            rerunButton.setVisibility(View.VISIBLE);
        });
    }

    private void hideRerunButton(Button runButton, Button skipButton, Button rerunButton) {
        runOnUiThread(() -> {
            runButton.setVisibility(View.VISIBLE);
            skipButton.setVisibility(View.VISIBLE);
            rerunButton.setVisibility(View.GONE);
        });
    }

    private void appendLog(String message) {
        Logger.logInfo(LOG_TAG, message);
        runOnUiThread(() -> {
            CharSequence current = logText.getText();
            String base = current == null ? "" : current.toString();
            String updated = base.isEmpty() ? message : base + "\n" + message;
            logText.setText(updated);
            logContainer.post(() -> logContainer.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void updateProgressPercent(int percent) {
        int clamped = Math.min(100, Math.max(0, percent));
        runOnUiThread(() -> {
            progressBar.setProgress(clamped);
            progressText.setText(clamped + "%");
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            errorText.setText(message);
            updateErrorVisibility(true);
        });
    }

    private void updateErrorVisibility(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        errorText.setVisibility(v);
        retryButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, DriverActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Downloads the lunar-adb-agent repository archive to the given directory,
     * mirroring the behavior of setup_lunar_adb_agent.sh but implemented in
     * Java so we don't rely on a shell script for network operations.
     */
    private void downloadLunarAdbAgent(java.io.File lunarDir) throws Exception {
        final String archiveUrl = "https://github.com/RiteshF7/lunar-adb-agent/archive/refs/heads/main.zip";
        final String archiveFileName = "lunar-adb-agent.zip";

        java.io.File archiveFile = new java.io.File(lunarDir, archiveFileName);

        java.net.URL url = new java.net.URL(archiveUrl);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);

        int response = connection.getResponseCode();
        if (response != java.net.HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("HTTP error " + response +
                " while downloading lunar-adb-agent archive");
        }

        byte[] buffer = new byte[8192];
        try (java.io.InputStream in = connection.getInputStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(archiveFile)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }

        appendLog("Extracting lunar-adb-agent archive...");

        // Use java.util.zip to extract the archive
        try (java.util.zip.ZipInputStream zipIn =
                 new java.util.zip.ZipInputStream(new java.io.FileInputStream(archiveFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String name = entry.getName();
                // Skip directory entries; they'll be created as needed
                java.io.File outFile = new java.io.File(lunarDir, name);
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        Logger.logError(LOG_TAG,
                            "Failed to create directory while extracting lunar-adb-agent: " +
                                outFile.getAbsolutePath());
                    }
                } else {
                    java.io.File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        Logger.logError(LOG_TAG,
                            "Failed to create parent directory while extracting lunar-adb-agent: " +
                                parent.getAbsolutePath());
                    }
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                        int len;
                        while ((len = zipIn.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }

        // Flatten "lunar-adb-agent-<branch>" directory into lunarDir
        java.io.File[] dirs = lunarDir.listFiles(java.io.File::isDirectory);
        if (dirs != null) {
            for (java.io.File dir : dirs) {
                if (dir.getName().startsWith("lunar-adb-agent-")) {
                    for (java.io.File child : dir.listFiles()) {
                        child.renameTo(new java.io.File(lunarDir, child.getName()));
                    }
                    dir.delete();
                    break;
                }
            }
        }

        // Delete the archive file
        if (!archiveFile.delete()) {
            Logger.logInfo(LOG_TAG,
                "Failed to delete lunar-adb-agent archive file: " + archiveFile.getAbsolutePath());
        }

        appendLog("lunar-adb-agent repository is ready at: " + lunarDir.getAbsolutePath());
    }
}

