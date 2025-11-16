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
    private ScrollView logContainer;

    private java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean isSetupInProgress = false;

    // Throttling state for progress/log updates to keep UI smooth during download.
    private volatile int lastDownloadProgressPercent = 0;
    private volatile long lastDownloadLogUpdateTimeMs = 0L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bootstrap_setup);

        progressBar = findViewById(R.id.bootstrap_setup_progress);
        progressText = findViewById(R.id.bootstrap_setup_progress_text);
        logText = findViewById(R.id.bootstrap_setup_log);
        errorText = findViewById(R.id.bootstrap_setup_error);
        retryButton = findViewById(R.id.bootstrap_setup_retry_button);
        logContainer = findViewById(R.id.bootstrap_setup_log_container);

        // Retry button click handler
        retryButton.setOnClickListener(v -> {
            errorText.setText("");
            startSetup();
        });

        // Start setup
        startSetup();
    }

    /**
     * Triggers bootstrap installation using TermuxInstaller.
     * This is called when download completes and we're ready to install.
     */
    private void installBootstrap() {
        // Update UI to show installation in progress
        logText.append("\nExtracting bootstrap archive...");
        progressBar.setProgress(92);
        progressText.setText("92%");
        logContainer.post(() -> logContainer.fullScroll(View.FOCUS_DOWN));
        
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
            // Installation complete
            runOnUiThread(() -> {
                logText.append("\nBootstrap installed successfully!");
                progressBar.setProgress(100);
                progressText.setText("100%");
                logContainer.post(() -> logContainer.fullScroll(View.FOCUS_DOWN));

                // Navigate to home screen after a short delay
                progressText.postDelayed(() -> {
                    Intent intent = new Intent(this, DriverActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }, 1000);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void startSetup() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        updateProgressPercent(0);
        appendLog("Checking if bootstrap is needed...");

        executor.execute(() -> {
            try {
                // Check if bootstrap already exists
                if (TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false) == null &&
                    !TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                    Logger.logInfo(LOG_TAG, "Bootstrap already installed");
                    appendLog("Bootstrap already installed, skipping setup.");
                    runOnUiThread(() -> {
                        progressBar.setProgress(100);
                        progressText.setText("100%");
                        navigateToHome();
                    });
                    return;
                }

                // Detect architecture
                String arch = BootstrapArchDetector.detectArch();
                appendLog("Detected architecture: " + arch);
                updateProgressPercent(5);

                // Check for local bootstrap file
                java.io.File localBootstrap = BootstrapDownloader.getLocalBootstrapFile(getApplication(), arch);
                String expectedChecksum = BootstrapConfig.getExpectedChecksum(arch);

                if (localBootstrap != null) {
                    if (!expectedChecksum.isEmpty() &&
                        !BootstrapDownloader.verifyChecksum(localBootstrap, expectedChecksum)) {
                        appendLog("Local bootstrap checksum invalid, will re-download...");
                        localBootstrap.delete();
                        localBootstrap = null;
                    } else {
                        appendLog("Using cached bootstrap file");
                        updateProgressPercent(20);
                    }
                }

                // Download if needed
                if (localBootstrap == null) {
                    appendLog("Downloading bootstrap from GitHub...");
                    updateProgressPercent(10);
                    lastDownloadProgressPercent = 10;
                    lastDownloadLogUpdateTimeMs = System.currentTimeMillis();

                    Error downloadError = BootstrapDownloader.downloadBootstrap(
                        getApplication(),
                        arch,
                        (downloaded, total) -> {
                            if (total > 0) {
                                int percent = 10 + (int) ((downloaded * 70) / total);

                                // Only update progress bar if percent actually changed
                                if (percent != lastDownloadProgressPercent) {
                                    lastDownloadProgressPercent = percent;
                                    updateProgressPercent(percent);
                                }

                                // Throttle log updates to at most ~2 per second
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
                        return;
                    }

                    appendLog("Download completed");
                    updateProgressPercent(80);
                }

                // Verify bootstrap file exists
                java.io.File bootstrapFile = BootstrapDownloader.getLocalBootstrapFile(getApplication(), arch);
                if (bootstrapFile == null || !bootstrapFile.exists() || bootstrapFile.length() == 0) {
                    String errorMsg = "Bootstrap file not found or empty after download";
                    Logger.logError(LOG_TAG, errorMsg);
                    showError(errorMsg);
                    return;
                }

                appendLog("Bootstrap file ready. Starting installation...");
                updateProgressPercent(90);

                runOnUiThread(this::installBootstrap);

            } catch (Exception e) {
                String errorMsg = "Setup failed: " + e.getMessage();
                Logger.logStackTraceWithMessage(LOG_TAG, "Setup failed", e);
                showError(errorMsg);
            } finally {
                isSetupInProgress = false;
            }
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
}

