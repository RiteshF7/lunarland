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
import com.termux.app.bootstrap.BootstrapArchDetector;
import com.termux.app.bootstrap.BootstrapConfig;
import com.termux.app.bootstrap.BootstrapDownloader;
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
    private boolean pipInstallInProgress = false;
    private boolean verificationInProgress = false;
    private boolean pipCheckInProgress = false;
    private boolean pipInstallInProgressState = false;
    private File pendingWheelFileForBootstrap = null;
    private static final AtomicInteger PIP_INSTALL_REQUEST_CODES = new AtomicInteger();
    private static final String ACTION_PIP_INSTALL_RESULT = "com.termux.app.action.PIP_INSTALL_RESULT";
    private BroadcastReceiver pipInstallReceiver;

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
        stepDownloadLunar.setOnClickListener(v -> performInstallLunar());
        stepSkipDownloadLunar.setOnClickListener(v -> skipInstallLunar());
        stepRerunDownloadLunar.setOnClickListener(v -> rerunInstallLunar());

        // Register BroadcastReceiver for pip install results
        pipInstallReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE);
                if (bundle != null) {
                    String stdout = bundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT, "");
                    String stderr = bundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR, "");
                    String errmsg = bundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG, "");
                    int exitCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, -1);
                    int errCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERR, Errno.ERRNO_FAILED.getCode());

                    runOnUiThread(() -> handlePipInstallResult(stdout, stderr, errmsg, exitCode, errCode));
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_PIP_INSTALL_RESULT);
        registerReceiver(pipInstallReceiver, filter);

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
        if (pipInstallReceiver != null) {
            try {
                unregisterReceiver(pipInstallReceiver);
            } catch (Exception e) {
                Logger.logWarn(LOG_TAG, "Failed to unregister pip install receiver: " + e.getMessage());
            }
        }
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

    private void performInstallLunar() {
        if (isSetupInProgress || pipInstallInProgress) {
            Logger.logWarn(LOG_TAG, "Setup already in progress");
            return;
        }

        isSetupInProgress = true;
        pipInstallInProgress = true;
        updateErrorVisibility(false);
        appendLog("Installing Lunar Agent (droidrun)...");
        disableButton(stepDownloadLunar);

        executor.execute(() -> {
            try {
                // Step 1: Extract wheel file from assets
                appendLog("Extracting wheel file from assets...");
                File wheelFile = extractWheelFromAssets();
                if (wheelFile == null || !wheelFile.exists()) {
                    String errorMsg = "Failed to extract wheel file from assets";
                    Logger.logError(LOG_TAG, errorMsg);
                    showError(errorMsg);
                    runOnUiThread(() -> {
                        enableButton(stepDownloadLunar);
                        pipInstallInProgress = false;
                    });
                    isSetupInProgress = false;
                    return;
                }
                appendLog("Wheel file extracted to: " + wheelFile.getAbsolutePath());

                // Step 2: Ensure pip is installed globally
                appendLog("Checking if pip is installed...");
                ensurePipInstalled(wheelFile);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG,
                    "Failed to install lunar-adb-agent", e);
                showError("Failed to install lunar-adb-agent: " + e.getMessage());
                runOnUiThread(() -> {
                    enableButton(stepDownloadLunar);
                    pipInstallInProgress = false;
                });
                isSetupInProgress = false;
            }
        });
    }

    private void skipInstallLunar() {
        appendLog("Skipped: Install Lunar Agent");
        stepDownloadLunar.setText("6. Install Lunar Agent (Skipped)");
        showRerunButton(stepDownloadLunar, stepSkipDownloadLunar, stepRerunDownloadLunar);
    }

    private void rerunInstallLunar() {
        appendLog("Rerunning: Install Lunar Agent");
        pipInstallInProgress = false;
        verificationInProgress = false;
        stepDownloadLunar.setText("6. Install Lunar Agent");
        hideRerunButton(stepDownloadLunar, stepSkipDownloadLunar, stepRerunDownloadLunar);
        stepDownloadLunar.setEnabled(true);
        stepSkipDownloadLunar.setEnabled(true);
        performInstallLunar();
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
     * Extracts the droidrun wheel file from assets to the home directory.
     */
    private File extractWheelFromAssets() {
        final String wheelAssetName = "droidrun-0.4.7-py3-none-any.whl";
        File wheelFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, wheelAssetName);
        
        try {
            AssetManager assetManager = getAssets();
            
            // Create parent directory if it doesn't exist
            File parentDir = wheelFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Logger.logError(LOG_TAG, "Failed to create parent directory: " + parentDir.getAbsolutePath());
                    return null;
                }
            }

            // Copy asset to file
            InputStream inputStream = assetManager.open(wheelAssetName);
            OutputStream outputStream = new FileOutputStream(wheelFile);
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            outputStream.close();
            inputStream.close();
            
            Logger.logInfo(LOG_TAG, "Extracted wheel file to: " + wheelFile.getAbsolutePath());
            return wheelFile;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to extract wheel file from assets", e);
            return null;
        }
    }

    /**
     * Ensures pip is installed globally before proceeding with installation.
     */
    private void ensurePipInstalled(File wheelFile) {
        pendingWheelFileForBootstrap = wheelFile;
        pipCheckInProgress = true;
        // Check if pip is available, if not install it
        String checkCommand = "python -m pip --version 2>&1 || echo 'PIP_NOT_FOUND'";
        
        Logger.logInfo(LOG_TAG, "Checking pip installation with command: " + checkCommand);
        appendLog("Checking pip installation...");
        
        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
            .build();

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
        execIntent.setClass(this, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", checkCommand});
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.getMode());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Check pip installation");
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createPipInstallPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Installs pip globally using pkg if it's not already installed.
     */
    private void installPipGlobally() {
        pipCheckInProgress = false;
        pipInstallInProgressState = true;
        appendLog("Installing pip and build tools globally using pkg...");
        
        // Install pip and essential build tools using pkg (Termux package manager)
        // Build tools are needed for compiling some Python packages
        // Also install common Python packages from Termux packages if available to avoid building from source:
        // - numpy, pandas, scipy, scikit-learn: data science libraries
        // - cryptography: security library
        // - lxml: XML parser
        // - pillow: image processing
        // - patchelf: system package (needed for building some Python packages like numpy)
        // If any package is not available, fall back to installing just build tools
        String installCommand = "pkg install -y python-pip autoconf automake libtool make clang cmake patchelf " +
                               "python-numpy python-pandas python-scipy python-scikit-learn " +
                               "python-cryptography python-lxml python-pillow 2>&1 || " +
                               "pkg install -y python-pip autoconf automake libtool make clang cmake patchelf";
        
        Logger.logInfo(LOG_TAG, "Installing pip and packages with command: " + installCommand);
        
        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
            .build();

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
        execIntent.setClass(this, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", installCommand});
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.getMode());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Install pip globally");
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createPipInstallPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Installs the wheel file using pip with [google] extra via TermuxService.
     * Installs globally (not user-specific) so it persists across sessions.
     */
    private void installWheelWithPip(File wheelFile) {
        String wheelPath = wheelFile.getAbsolutePath();
        // Use python -m pip without --user flag to install globally
        // Upgrade pip, wheel, setuptools first, then install wheel file, then google extra
        // Use --prefer-binary to prefer pre-built wheels over building from source
        // Both installs are global (system-wide) in Termux
        // Ensure PATH includes Termux bin directory for autotools, and install packages
        // Use --prefer-binary to prefer pre-built wheels, but allow building if needed
        String command = "export PATH=$PREFIX/bin:$PATH && " +
                         "python -m pip install --upgrade pip wheel setuptools && " +
                         "python -m pip install \"" + wheelPath + "\" && " +
                         "python -m pip install --prefer-binary \"droidrun[google]\"";
        
        Logger.logInfo(LOG_TAG, "Installing droidrun wheel with command: " + command);
        Logger.logInfo(LOG_TAG, "Wheel file path: " + wheelPath);
        appendLog("Executing: " + command);
        
        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
            .build();

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
        execIntent.setClass(this, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", command});
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.getMode());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Install droidrun[google]");
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createPipInstallPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Creates a PendingIntent for pip install command results.
     */
    private PendingIntent createPipInstallPendingIntent() {
        Intent resultIntent = new Intent(ACTION_PIP_INSTALL_RESULT).setPackage(getPackageName());
        int requestCode = PIP_INSTALL_REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }

    /**
     * Handles the result of pip install command execution and verification.
     */
    private void handlePipInstallResult(String stdout, String stderr, String errmsg, int exitCode, int errCode) {
        Logger.logVerbose(LOG_TAG, "handlePipInstallResult - exitCode: " + exitCode + ", errCode: " + errCode);
        Logger.logVerbose(LOG_TAG, "handlePipInstallResult - pipCheckInProgress: " + pipCheckInProgress + ", pipInstallInProgressState: " + pipInstallInProgressState + ", verificationInProgress: " + verificationInProgress);
        
        appendLog("Command stdout: " + stdout);
        Logger.logVerbose(LOG_TAG, "Command stdout: " + stdout);
        
        if (!stderr.isEmpty()) {
            appendLog("Command stderr: " + stderr);
            Logger.logError(LOG_TAG, "Command stderr: " + stderr);
        }
        if (!errmsg.isEmpty()) {
            appendLog("Command error: " + errmsg);
            Logger.logError(LOG_TAG, "Command error: " + errmsg);
        }

        if (pipCheckInProgress) {
            // This is a pip check result
            Logger.logInfo(LOG_TAG, "Pip check result - exitCode: " + exitCode + ", stdout: " + stdout.substring(0, Math.min(200, stdout.length())));
            pipCheckInProgress = false;
            if (exitCode == 0 && !stdout.contains("PIP_NOT_FOUND") && !stderr.contains("No module named pip")) {
                // Pip is installed, proceed with wheel installation
                Logger.logInfo(LOG_TAG, "Pip is already installed. Proceeding with droidrun installation...");
                appendLog("Pip is already installed. Proceeding with droidrun installation...");
                if (pendingWheelFileForBootstrap != null) {
                    appendLog("Installing droidrun with Google Gemini support...");
                    installWheelWithPip(pendingWheelFileForBootstrap);
                }
            } else {
                // Pip is not installed, install it first
                Logger.logInfo(LOG_TAG, "Pip is not installed. Installing pip and build tools globally...");
                appendLog("Pip is not installed. Installing pip and build tools globally...");
                installPipGlobally();
            }
        } else if (pipInstallInProgressState) {
            // This is a pip installation result
            Logger.logInfo(LOG_TAG, "Pip installation result - exitCode: " + exitCode);
            Logger.logVerbose(LOG_TAG, "Pip installation stdout: " + stdout.substring(0, Math.min(500, stdout.length())));
            if (!stderr.isEmpty()) {
                Logger.logError(LOG_TAG, "Pip installation stderr: " + stderr.substring(0, Math.min(500, stderr.length())));
            }
            pipInstallInProgressState = false;
            if (exitCode == 0) {
                Logger.logInfo(LOG_TAG, "Pip and build tools installed successfully. Proceeding with droidrun installation...");
                appendLog("Pip and build tools installed successfully. Proceeding with droidrun installation...");
                if (pendingWheelFileForBootstrap != null) {
                    appendLog("Installing droidrun with Google Gemini support...");
                    installWheelWithPip(pendingWheelFileForBootstrap);
                }
            } else {
                String errorMsg = "Failed to install pip/build tools with exit code " + exitCode;
                if (!stderr.isEmpty()) {
                    errorMsg += ": " + stderr;
                    Logger.logError(LOG_TAG, "Full pip installation stderr: " + stderr);
                }
                Logger.logError(LOG_TAG, errorMsg);
                showError(errorMsg);
                runOnUiThread(() -> {
                    enableButton(stepDownloadLunar);
                    pipInstallInProgress = false;
                });
                isSetupInProgress = false;
                pendingWheelFileForBootstrap = null;
            }
        } else if (verificationInProgress) {
            // This is a verification result
            verificationInProgress = false;
            if (exitCode == 0 && stdout.contains("droidrun installed successfully")) {
                appendLog("Verification successful! droidrun is installed correctly.");
                runOnUiThread(() -> {
                    stepDownloadLunar.setText("6. Install Lunar Agent ✓");
                    showRerunButton(stepDownloadLunar, stepSkipDownloadLunar, stepRerunDownloadLunar);
                    pipInstallInProgress = false;
                });
                isSetupInProgress = false;
                pendingWheelFileForBootstrap = null;
            } else {
                String errorMsg = "Verification failed. droidrun may not be installed correctly.";
                if (!stderr.isEmpty()) {
                    errorMsg += " Error: " + stderr;
                }
                Logger.logError(LOG_TAG, errorMsg);
                showError(errorMsg);
                runOnUiThread(() -> {
                    enableButton(stepDownloadLunar);
                    pipInstallInProgress = false;
                });
                isSetupInProgress = false;
                pendingWheelFileForBootstrap = null;
            }
        } else {
            // This is a droidrun pip install result
            Logger.logInfo(LOG_TAG, "Droidrun pip install result - exitCode: " + exitCode);
            Logger.logVerbose(LOG_TAG, "Droidrun install stdout (first 1000 chars): " + stdout.substring(0, Math.min(1000, stdout.length())));
            if (!stderr.isEmpty()) {
                Logger.logError(LOG_TAG, "Droidrun install stderr (first 1000 chars): " + stderr.substring(0, Math.min(1000, stderr.length())));
                // Log full stderr for debugging missing packages
                Logger.logError(LOG_TAG, "Droidrun install FULL stderr: " + stderr);
            }
            if (exitCode == 0) {
                Logger.logInfo(LOG_TAG, "Droidrun pip install completed successfully. Verifying installation...");
                appendLog("Pip install completed successfully. Verifying installation...");
                verificationInProgress = true;
                verifyInstallation();
            } else {
                String errorMsg = "Pip install failed with exit code " + exitCode;
                if (!stderr.isEmpty()) {
                    errorMsg += ": " + stderr.substring(0, Math.min(500, stderr.length()));
                    // Log the full error for debugging
                    Logger.logError(LOG_TAG, "Droidrun install FULL error output: " + stderr);
                }
                Logger.logError(LOG_TAG, errorMsg);
                showError(errorMsg);
                runOnUiThread(() -> {
                    enableButton(stepDownloadLunar);
                    pipInstallInProgress = false;
                });
                isSetupInProgress = false;
                pendingWheelFileForBootstrap = null;
            }
        }
    }

    /**
     * Verifies that droidrun was installed successfully by importing it.
     */
    private void verifyInstallation() {
        String command = "python -c \"import droidrun; print('droidrun installed successfully')\"";
        
        Logger.logInfo(LOG_TAG, "Verifying droidrun installation with command: " + command);
        appendLog("Verifying installation: " + command);
        
        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
            .build();

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
        execIntent.setClass(this, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", command});
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.getMode());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Verify droidrun installation");
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createVerificationPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Creates a PendingIntent for verification command results.
     */
    private PendingIntent createVerificationPendingIntent() {
        Intent resultIntent = new Intent(ACTION_PIP_INSTALL_RESULT).setPackage(getPackageName());
        int requestCode = PIP_INSTALL_REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }
}

