package com.termux.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.bootstrap.BootstrapDownloader;
import com.termux.app.bootstrap.BootstrapConfig;
import com.termux.shared.errors.Error;
import com.termux.shared.errors.Errno;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.file.TermuxFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

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

    private void performCheckBootstrap() {
        if (isSetupInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Checking if bootstrap is already installed...");
        disableButton(stepCheckBootstrap);

        executor.execute(() -> {
            try {
                boolean installed = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false) == null &&
                                   !TermuxFileUtils.isTermuxPrefixDirectoryEmpty();
                
                bootstrapAlreadyInstalled = installed;
                
                    runOnUiThread(() -> {
                    if (installed) {
                        appendLog("Bootstrap is already installed.");
                        stepCheckBootstrap.setText("1. Check Bootstrap ✓");
                        showRerunButton(stepCheckBootstrap, stepSkipCheckBootstrap, stepRerunCheckBootstrap);
                } else {
                        appendLog("Bootstrap is not installed.");
                        stepCheckBootstrap.setText("1. Check Bootstrap (Not Installed)");
                        showRerunButton(stepCheckBootstrap, stepSkipCheckBootstrap, stepRerunCheckBootstrap);
                    }
                    enableButton(stepCheckBootstrap);
                    isSetupInProgress = false;
                    });
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error checking bootstrap", e);
                runOnUiThread(() -> {
                    showError("Error checking bootstrap: " + e.getMessage());
                    enableButton(stepCheckBootstrap);
                isSetupInProgress = false;
                });
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
        appendLog("Detecting device architecture...");
        disableButton(stepDetectArch);

        executor.execute(() -> {
            try {
                String arch = com.termux.app.bootstrap.BootstrapArchDetector.detectArch();
                detectedArch = arch;
                
                runOnUiThread(() -> {
                    appendLog("Detected architecture: " + arch);
                    stepDetectArch.setText("2. Detect Architecture ✓ (" + arch + ")");
                    showRerunButton(stepDetectArch, stepSkipDetectArch, stepRerunDetectArch);
                    enableButton(stepDetectArch);
                    isSetupInProgress = false;
                });
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error detecting architecture", e);
                runOnUiThread(() -> {
                    showError("Error detecting architecture: " + e.getMessage());
                    enableButton(stepDetectArch);
                    isSetupInProgress = false;
                });
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

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Checking for local bootstrap file...");
        disableButton(stepCheckLocal);

        executor.execute(() -> {
            try {
                if (detectedArch == null) {
                    runOnUiThread(() -> {
                        showError("Please detect architecture first");
                        enableButton(stepCheckLocal);
                        isSetupInProgress = false;
                    });
                    return;
                }

                File localFile = BootstrapDownloader.getLocalBootstrapFile(this, detectedArch);
                localBootstrapFile = localFile;

                runOnUiThread(() -> {
                    if (localFile != null && localFile.exists() && localFile.length() > 0) {
                        appendLog("Local bootstrap file found: " + localFile.getAbsolutePath());
                    stepCheckLocal.setText("3. Check Local File ✓");
                    } else {
                        appendLog("No local bootstrap file found.");
                        stepCheckLocal.setText("3. Check Local File (Not Found)");
                    }
                    showRerunButton(stepCheckLocal, stepSkipCheckLocal, stepRerunCheckLocal);
                    enableButton(stepCheckLocal);
                    isSetupInProgress = false;
                });
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error checking local file", e);
                runOnUiThread(() -> {
                    showError("Error checking local file: " + e.getMessage());
                    enableButton(stepCheckLocal);
                isSetupInProgress = false;
                });
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
            showError("Please detect architecture first");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Downloading bootstrap package...");
        disableButton(stepDownload);

        executor.execute(() -> {
            try {
                BootstrapDownloader.ProgressCallback progressCallback = 
                    (downloaded, total) -> {
                        if (total > 0) {
                            int percent = (int) ((downloaded * 100) / total);
                            if (percent != lastDownloadProgressPercent) {
                                lastDownloadProgressPercent = percent;
                                long now = System.currentTimeMillis();
                                if (now - lastDownloadLogUpdateTimeMs > 500) {
                                    lastDownloadLogUpdateTimeMs = now;
                                    runOnUiThread(() -> {
                                updateProgressPercent(percent);
                                        appendLog("Downloading: " + percent + "%");
                                    });
                                }
                            }
                        }
                    };

                Error downloadError = BootstrapDownloader.downloadBootstrap(
                    this,
                    detectedArch,
                    progressCallback
                );

                if (downloadError != null) {
                    String errorMsg = "Download failed: " + downloadError.getMessage();
                    Logger.logError(LOG_TAG, errorMsg);
                    runOnUiThread(() -> {
                    showError(errorMsg);
                        enableButton(stepDownload);
                        isSetupInProgress = false;
                    });
                    return;
                }

                File localFile = BootstrapDownloader.getLocalBootstrapFile(this, detectedArch);
                localBootstrapFile = localFile;

                runOnUiThread(() -> {
                    appendLog("Download completed successfully.");
                    stepDownload.setText("4. Download Bootstrap ✓");
                    showRerunButton(stepDownload, stepSkipDownload, stepRerunDownload);
                    enableButton(stepDownload);
                    isSetupInProgress = false;
                });
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error downloading bootstrap", e);
                runOnUiThread(() -> {
                    showError("Error downloading bootstrap: " + e.getMessage());
                    enableButton(stepDownload);
                isSetupInProgress = false;
                });
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

        if (localBootstrapFile == null || !localBootstrapFile.exists()) {
            showError("Please download bootstrap first");
            return;
        }

        isSetupInProgress = true;
        updateErrorVisibility(false);
        appendLog("Installing bootstrap package...");
        disableButton(stepInstall);

        executor.execute(() -> {
            try {
                installBootstrapWithLunarAgentSetup();

        runOnUiThread(() -> {
                    appendLog("Installation completed successfully.");
            stepInstall.setText("5. Install Bootstrap ✓");
            showRerunButton(stepInstall, stepSkipInstall, stepRerunInstall);
                    enableButton(stepInstall);
                    isSetupInProgress = false;
                });
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error installing bootstrap", e);
                runOnUiThread(() -> {
                    showError("Error installing bootstrap: " + e.getMessage());
                    enableButton(stepInstall);
                    isSetupInProgress = false;
                });
            }
        });
    }

    private void skipInstall() {
        appendLog("Skipped: Install Bootstrap");
        stepInstall.setText("5. Install Bootstrap (Skipped)");
        showRerunButton(stepInstall, stepSkipInstall, stepRerunInstall);
    }

    private void rerunInstall() {
        appendLog("Rerunning: Install Bootstrap");
        stepInstall.setText("5. Install Bootstrap");
        hideRerunButton(stepInstall, stepSkipInstall, stepRerunInstall);
        stepInstall.setEnabled(true);
        stepSkipInstall.setEnabled(true);
        performInstall();
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
}
