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
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.termux.R;
import com.termux.shared.errors.Errno;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

/**
 * Minimal activity that bridges user-entered commands to {@link TermuxService}.
 */
public class DriverActivity extends AppCompatActivity {

    private static final String ACTION_DRIVER_RESULT = "com.termux.app.action.DRIVER_RESULT";
    private static final String ACTION_INSTALL_LUNAR_RESULT = "com.termux.app.action.INSTALL_LUNAR_RESULT";
    private static final int OUTPUT_PREVIEW_LIMIT = 400;
    private static final AtomicInteger REQUEST_CODES = new AtomicInteger();
    private static final AtomicInteger INSTALL_LUNAR_REQUEST_CODES = new AtomicInteger();
    private static final String LOG_TAG = "DriverActivity";
    private static final String LOGCAT_COMMAND_TAG = Logger.getDefaultLogTag() + "Command";

    private EditText commandInput;
    private TextView statusView;
    private Button executeButton;
    private Button launchTermuxButton;
    private Button installLunarButton;
    private Button launchLunarHomeScreenButton;
    private NestedScrollView logsContainer;
    private TextView logsView;
    private final StringBuilder logBuffer = new StringBuilder();

    private boolean bootstrapReady;
    private boolean bootstrapInProgress;
    private boolean receiverRegistered;
    private boolean installLunarInProgress = false;
    private boolean verificationInProgress = false;
    private boolean pipCheckInProgress = false;
    private boolean pipInstallInProgress = false;
    private boolean numpyPandasCheckInProgress = false;
    private Runnable numpyPandasCheckCallback = null;
    private File pendingWheelFile = null;
    private LogcatWatcher logcatWatcher;
    private String activeCommand;
    
    // ViewModel instance
    private DriverViewModel viewModel;

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_INSTALL_LUNAR_RESULT.equals(intent.getAction())) {
                Bundle bundle = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE);
                if (bundle != null) {
                    String stdout = bundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT, "");
                    String stderr = bundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR, "");
                    String errmsg = bundle.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG, "");
                    int exitCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, -1);
                    int errCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERR, Errno.ERRNO_FAILED.getCode());
                    runOnUiThread(() -> handleInstallLunarResult(stdout, stderr, errmsg, exitCode, errCode));
                }
            } else {
                BundleExtras extras = BundleExtras.from(intent);
                runOnUiThread(() -> {
                    if (extras == null) {
                        handleMissingResult();
                    } else {
                        handleCommandResult(extras);
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        // Initialize ViewModel - this survives configuration changes
        viewModel = new ViewModelProvider(this).get(DriverViewModel.class);
        Logger.logInfo(LOG_TAG, "ViewModel initialized: " + viewModel.getViewModelStatus());

        commandInput = findViewById(R.id.driver_command_input);
        statusView = findViewById(R.id.driver_status_view);
        executeButton = findViewById(R.id.driver_execute_button);
        launchTermuxButton = findViewById(R.id.driver_launch_termux_button);
        installLunarButton = findViewById(R.id.driver_install_lunar_button);
        launchLunarHomeScreenButton = findViewById(R.id.driver_launch_lunar_homescreen_button);
        logsContainer = findViewById(R.id.driver_logs_container);
        logsView = findViewById(R.id.driver_logs_view);
        Button taskExecutorButton = findViewById(R.id.driver_open_task_executor_button);

        // Restore state from ViewModel if available
        if (viewModel.isBootstrapReady()) {
            bootstrapReady = true;
            executeButton.setEnabled(true);
            if (launchTermuxButton != null) {
                launchTermuxButton.setEnabled(true);
            }
            statusView.setText(getString(R.string.driver_status_ready) + " - " + viewModel.getViewModelStatus());
        } else {
            statusView.setText(R.string.driver_status_bootstrapping);
            executeButton.setEnabled(false);
            if (launchTermuxButton != null) {
                launchTermuxButton.setEnabled(false);
            }
        }
        if (launchTermuxButton != null) {
            launchTermuxButton.setOnClickListener(view -> launchTermuxActivity());
        }
        if (taskExecutorButton != null) {
            taskExecutorButton.setOnClickListener(view ->
                startActivity(new Intent(this, TaskExecutorActivity.class))
            );
        }
        if (installLunarButton != null) {
            installLunarButton.setOnClickListener(view -> performInstallLunar());
        }
        if (launchLunarHomeScreenButton != null) {
            launchLunarHomeScreenButton.setOnClickListener(view -> {
                Intent intent = new Intent(this, LunarHomeScreenActivity.class);
                startActivity(intent);
            });
        }

        executeButton.setOnClickListener(view -> executeCommand(commandInput.getText().toString()));
        commandInput.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerResultReceiver();
        ensureBootstrapSetup();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterResultReceiver();
        stopLogcatWatcher();
    }

    private void executeCommand(String rawCommand) {
        if (!bootstrapReady) {
            statusView.setText(R.string.driver_status_bootstrapping);
            ensureBootstrapSetup();
            return;
        }

        if (TextUtils.isEmpty(rawCommand)) {
            statusView.setText(R.string.driver_error_empty_command);
            return;
        }

        resetLogs();
        String command = adjustCommand(rawCommand.trim());
        activeCommand = command;
        
        // Update ViewModel with command execution
        viewModel.setCommandExecuting(true);
        viewModel.setLastExecutedCommand(command);
        appendConsoleLine("Executing: " + command);
        appendConsoleLine("ViewModel Status: " + viewModel.getViewModelStatus());

        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
            .build();

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
        execIntent.setClass(this, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", command});
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.getMode());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, getString(R.string.driver_command_label, command));
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createResultPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }

        startLogcatWatcher(command);
        statusView.setText(getString(R.string.driver_status_executing, command));
        executeButton.setEnabled(false);
        commandInput.getText().clear();
    }

    private PendingIntent createResultPendingIntent() {
        Intent resultIntent = new Intent(ACTION_DRIVER_RESULT).setPackage(getPackageName());
        int requestCode = REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }

    private String adjustCommand(String command) {
        String normalized = command;
        if (command.startsWith("pkg install") && !command.contains("-y")) {
            normalized = command.replaceFirst("pkg install", "pkg install -y");
            appendConsoleLine("Auto-appended -y to pkg install for non-interactive execution.");
        } else if (command.startsWith("apt install") && !command.contains("-y")) {
            normalized = command.replaceFirst("apt install", "apt install -y");
            appendConsoleLine("Auto-appended -y to apt install for non-interactive execution.");
        }
        Logger.logInfo(LOG_TAG, "Executing command: " + normalized);
        return normalized;
    }

    private void ensureBootstrapSetup() {
        if (bootstrapReady || bootstrapInProgress) return;
        
        // Check if bootstrap prefix exists
        if (com.termux.shared.termux.file.TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false) == null &&
            !com.termux.shared.termux.file.TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
            bootstrapReady = true;
            
            // Update ViewModel
            viewModel.setBootstrapReady(true);
            
            executeButton.setEnabled(true);
            if (launchTermuxButton != null) {
                launchTermuxButton.setEnabled(true);
            }
            if (installLunarButton != null) {
                installLunarButton.setEnabled(true);
            }
            statusView.setText(getString(R.string.driver_status_ready) + " - " + viewModel.getViewModelStatus());
            return;
        }
        
        // Bootstrap not installed - redirect to BootstrapSetupActivity
        Intent intent = new Intent(this, BootstrapSetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handleCommandResult(BundleExtras extras) {
        bootstrapReady = true;
        bootstrapInProgress = false;
        
        // Update ViewModel
        viewModel.setBootstrapReady(true);
        viewModel.setCommandExecuting(false);
        
        executeButton.setEnabled(true);
        if (launchTermuxButton != null) {
            launchTermuxButton.setEnabled(true);
        }
        if (installLunarButton != null) {
            installLunarButton.setEnabled(true);
        }
        stopLogcatWatcher();

        if (extras.errCode == Errno.ERRNO_SUCCESS.getCode()) {
            String output = !extras.stdout.isEmpty() ? extras.stdout : extras.stderr;
            output = trimOutput(output);
            
            // Store output in ViewModel
            viewModel.setLastCommandOutput(output);
            
            if (output.isEmpty()) {
                String status = getString(R.string.driver_status_success_no_output, extras.exitCode);
                statusView.setText(status + " - " + viewModel.getViewModelStatus());
                Logger.logInfo(LOG_TAG, "Command finished (exit " + extras.exitCode + ") with no output");
            } else {
                String status = getString(R.string.driver_status_success, extras.exitCode, output);
                statusView.setText(status + " - " + viewModel.getViewModelStatus());
                Logger.logInfo(LOG_TAG, "Command finished (exit " + extras.exitCode + "): " + output);
            }
        } else {
            String message = !extras.errmsg.isEmpty() ? extras.errmsg : extras.stderr;
            message = trimOutput(message);
            
            // Store error in ViewModel
            viewModel.setLastCommandOutput(message);
            
            if (!extras.stderr.isEmpty() && !extras.stderr.equals(message)) {
                String status = getString(R.string.driver_status_error_with_output, extras.errCode, message, trimOutput(extras.stderr));
                statusView.setText(status + " - " + viewModel.getViewModelStatus());
                Logger.logError(LOG_TAG, "Command failed (err " + extras.errCode + "): " + message + " | stderr: " + trimOutput(extras.stderr));
            } else {
                String status = getString(R.string.driver_status_error, extras.errCode, message.isEmpty() ? "Unknown error" : message);
                statusView.setText(status + " - " + viewModel.getViewModelStatus());
                Logger.logError(LOG_TAG, "Command failed (err " + extras.errCode + "): " + (message.isEmpty() ? "Unknown error" : message));
            }
        }
    }

    private String trimOutput(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= OUTPUT_PREVIEW_LIMIT) return trimmed;
        return trimmed.substring(0, OUTPUT_PREVIEW_LIMIT) + "...";
    }

    private void registerResultReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(ACTION_DRIVER_RESULT);
        filter.addAction(ACTION_INSTALL_LUNAR_RESULT);
        registerReceiver(resultReceiver, filter);
        receiverRegistered = true;
    }

    private void handleMissingResult() {
        bootstrapReady = true;
        bootstrapInProgress = false;
        executeButton.setEnabled(true);
        if (launchTermuxButton != null) {
            launchTermuxButton.setEnabled(true);
        }
        if (installLunarButton != null) {
            installLunarButton.setEnabled(true);
        }
        statusView.setText(R.string.driver_status_result_missing);
        stopLogcatWatcher();
        Logger.logWarn(LOG_TAG, "Command finished but no result bundle was returned");
    }

    private void unregisterResultReceiver() {
        if (!receiverRegistered) return;
        try {
            unregisterReceiver(resultReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver already unregistered; ignore.
        }
        receiverRegistered = false;
    }

    private void resetLogs() {
        logBuffer.setLength(0);
        if (logsView != null) {
            logsView.setText("");
        }
    }

    private void appendConsoleLine(String line) {
        if (TextUtils.isEmpty(line) || logsView == null) return;
        // Ensure UI updates happen on the main thread
        runOnUiThread(() -> {
            if (logBuffer.length() > 0) logBuffer.append('\n');
            logBuffer.append(line);
            logsView.setText(logBuffer.toString());
            if (logsContainer != null) {
                logsContainer.post(() -> logsContainer.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void startLogcatWatcher(String command) {
        stopLogcatWatcher();
        logcatWatcher = new LogcatWatcher(command, new LogcatWatcher.Callback() {
            @Override
            public void onLine(String line) {
                runOnUiThread(() -> appendConsoleLine(line));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> appendConsoleLine(message));
            }
        });
        logcatWatcher.start();
    }

    private void stopLogcatWatcher() {
        if (logcatWatcher != null) {
            logcatWatcher.stopWatching();
            logcatWatcher = null;
        }
    }

    private void launchTermuxActivity() {
        Intent intent = new Intent(this, TermuxActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLogcatWatcher();
    }

    private static final class BundleExtras {
        final String stdout;
        final String stderr;
        final String errmsg;
        final int exitCode;
        final int errCode;

        private BundleExtras(String stdout, String stderr, String errmsg, int exitCode, int errCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.errmsg = errmsg;
            this.exitCode = exitCode;
            this.errCode = errCode;
        }

        static BundleExtras from(Intent intent) {
            if (intent == null) return null;
            Bundle bundle = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE);
            if (bundle == null) return null;

            String stdout = getString(bundle, TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT);
            String stderr = getString(bundle, TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR);
            String errmsg = getString(bundle, TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG);
            int exitCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, 0);
            int errCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERR, Errno.ERRNO_FAILED.getCode());

            return new BundleExtras(stdout, stderr, errmsg, exitCode, errCode);
        }

        private static String getString(Bundle bundle, String key) {
            String value = bundle.getString(key, "");
            return value == null ? "" : value;
        }
    }

    private static final class LogcatWatcher {

        interface Callback {
            void onLine(String line);
            void onError(String message);
        }

        private final String commandTrigger;
        private final Callback callback;
        private Process process;
        private Thread thread;
        private volatile boolean running;
        private boolean capturing;

        LogcatWatcher(String commandTrigger, Callback callback) {
            this.commandTrigger = commandTrigger;
            this.callback = callback;
        }

        void start() {
            running = true;
            thread = new Thread(() -> {
                try {
                    ProcessBuilder builder = new ProcessBuilder("logcat",
                        "--pid=" + android.os.Process.myPid(),
                        "-v", "brief");
                    process = builder.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while (running && (line = reader.readLine()) != null) {
                            handleLine(line);
                        }
                    }
                } catch (IOException e) {
                    if (callback != null) {
                        callback.onError("Logcat watcher error: " + e.getMessage());
                    }
                }
            }, "LogcatWatcher");
            thread.start();
        }

        void stopWatching() {
            running = false;
            if (process != null) {
                process.destroy();
            }
            if (thread != null) {
                try {
                    thread.join(200);
                } catch (InterruptedException ignored) {
                }
            }
        }

        private void handleLine(String raw) {
            if (TextUtils.isEmpty(raw)) return;
            if (!capturing) {
                if (raw.contains(DriverActivity.LOG_TAG) && raw.contains("Executing command: " + commandTrigger)) {
                    capturing = true;
                } else {
                    return;
                }
            }

            String sanitized = sanitize(raw);
            if (sanitized != null && callback != null) {
                callback.onLine(sanitized);
            }
        }

        private String sanitize(String raw) {
            if (raw.contains(LOGCAT_COMMAND_TAG)) {
                String message = extractMessage(raw);
                if (message.startsWith("[") && message.contains("]")) {
                    message = message.substring(message.indexOf(']') + 1).trim();
                }
                return message;
            } else if (raw.contains(DriverActivity.LOG_TAG) || raw.contains("Termux." + DriverActivity.LOG_TAG)) {
                return extractMessage(raw);
            }
            return null;
        }

        private String extractMessage(String raw) {
            int idx = raw.indexOf(": ");
            if (idx >= 0 && idx + 2 < raw.length()) {
                return raw.substring(idx + 2);
            }
            return raw;
        }
    }

    /**
     * Performs installation of Lunar Agent (droidrun) from wheel file.
     */
    private void performInstallLunar() {
        if (installLunarInProgress) {
            Logger.logWarn(LOG_TAG, "Install Lunar Agent already in progress");
            return;
        }

        if (!bootstrapReady) {
            appendConsoleLine("Bootstrap not ready. Please wait for bootstrap to complete.");
            statusView.setText("Bootstrap not ready");
            return;
        }

        installLunarInProgress = true;
        runOnUiThread(() -> {
            if (installLunarButton != null) {
                installLunarButton.setEnabled(false);
            }
            statusView.setText("Installing Lunar Agent (droidrun)...");
            appendConsoleLine("Installing Lunar Agent (droidrun)...");
        });

        new Thread(() -> {
            try {
                // Step 1: Extract wheel file from assets
                runOnUiThread(() -> appendConsoleLine("Extracting wheel file from assets..."));
                File wheelFile = extractWheelFromAssets();
                if (wheelFile == null || !wheelFile.exists()) {
                    String errorMsg = "Failed to extract wheel file from assets";
                    Logger.logError(LOG_TAG, errorMsg);
                    runOnUiThread(() -> {
                        statusView.setText("Error: " + errorMsg);
                        if (installLunarButton != null) {
                            installLunarButton.setEnabled(true);
                        }
                        installLunarInProgress = false;
                    });
                    return;
                }
                runOnUiThread(() -> appendConsoleLine("Wheel file extracted to: " + wheelFile.getAbsolutePath()));

                // Step 2: Ensure pip is installed globally
                pendingWheelFile = wheelFile;
                runOnUiThread(() -> appendConsoleLine("Checking if pip is installed..."));
                ensurePipInstalled();
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to install lunar-adb-agent", e);
                runOnUiThread(() -> {
                    statusView.setText("Error: " + e.getMessage());
                    if (installLunarButton != null) {
                        installLunarButton.setEnabled(true);
                    }
                    installLunarInProgress = false;
                });
            }
        }).start();
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
    private void ensurePipInstalled() {
        pipCheckInProgress = true;
        // Check if pip is available, if not install it
        String checkCommand = "python -m pip --version 2>&1 || echo 'PIP_NOT_FOUND'";
        
        Logger.logInfo(LOG_TAG, "Checking pip installation with command: " + checkCommand);
        runOnUiThread(() -> appendConsoleLine("Checking pip installation..."));
        
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
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createCheckPipPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Creates a PendingIntent for pip check command results.
     */
    private PendingIntent createCheckPipPendingIntent() {
        Intent resultIntent = new Intent(ACTION_INSTALL_LUNAR_RESULT).setPackage(getPackageName());
        int requestCode = INSTALL_LUNAR_REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }

    /**
     * Installs pip globally using pkg if it's not already installed.
     */
    private void installPipGlobally() {
        pipCheckInProgress = false;
        pipInstallInProgress = true;
        runOnUiThread(() -> appendConsoleLine("Installing pip and build tools globally using pkg..."));
        
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
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createInstallPipPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Creates a PendingIntent for pip installation command results.
     */
    private PendingIntent createInstallPipPendingIntent() {
        Intent resultIntent = new Intent(ACTION_INSTALL_LUNAR_RESULT).setPackage(getPackageName());
        int requestCode = INSTALL_LUNAR_REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }

    /**
     * Installs the wheel file using pip with [google] extra via TermuxService.
     * Installs globally (not user-specific) so it persists across sessions.
     */
    private void installWheelWithPip(File wheelFile) {
        String wheelPath = wheelFile.getAbsolutePath();
        // Use python -m pip without --user flag to install globally
        // Install wheel first, then install the google extra
        // Use --no-build-isolation and --only-binary to prefer pre-built wheels
        // Both installs are global (system-wide) in Termux
        // Ensure PATH includes Termux bin directory for autotools, and install packages
        // Use --prefer-binary to prefer pre-built wheels, but allow building if needed
        String command = "export PATH=$PREFIX/bin:$PATH && " +
                         "python -m pip install --upgrade pip wheel setuptools && " +
                         "python -m pip install \"" + wheelPath + "\" && " +
                         "python -m pip install --prefer-binary \"droidrun[google]\"";
        
        Logger.logInfo(LOG_TAG, "Installing droidrun wheel with command: " + command);
        Logger.logInfo(LOG_TAG, "Wheel file path: " + wheelPath);
        runOnUiThread(() -> appendConsoleLine("Executing: " + command));
        
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
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createInstallLunarPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Creates a PendingIntent for install lunar command results.
     */
    private PendingIntent createInstallLunarPendingIntent() {
        Intent resultIntent = new Intent(ACTION_INSTALL_LUNAR_RESULT).setPackage(getPackageName());
        int requestCode = INSTALL_LUNAR_REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }

    /**
     * Handles the result of install lunar command execution and verification.
     */
    private void handleInstallLunarResult(String stdout, String stderr, String errmsg, int exitCode, int errCode) {
        Logger.logVerbose(LOG_TAG, "handleInstallLunarResult - exitCode: " + exitCode + ", errCode: " + errCode);
        Logger.logVerbose(LOG_TAG, "handleInstallLunarResult - pipCheckInProgress: " + pipCheckInProgress + ", pipInstallInProgress: " + pipInstallInProgress + ", verificationInProgress: " + verificationInProgress);
        
        appendConsoleLine("Command stdout: " + stdout);
        Logger.logVerbose(LOG_TAG, "Command stdout: " + stdout);
        
        if (!stderr.isEmpty()) {
            appendConsoleLine("Command stderr: " + stderr);
            Logger.logError(LOG_TAG, "Command stderr: " + stderr);
        }
        if (!errmsg.isEmpty()) {
            appendConsoleLine("Command error: " + errmsg);
            Logger.logError(LOG_TAG, "Command error: " + errmsg);
        }

        if (pipCheckInProgress) {
            // This is a pip check result
            Logger.logInfo(LOG_TAG, "Pip check result - exitCode: " + exitCode + ", stdout: " + stdout.substring(0, Math.min(200, stdout.length())));
            pipCheckInProgress = false;
            if (exitCode == 0 && !stdout.contains("PIP_NOT_FOUND") && !stderr.contains("No module named pip")) {
                // Pip is installed, verify numpy/pandas before proceeding
                Logger.logInfo(LOG_TAG, "Pip is already installed. Verifying numpy/pandas installation...");
                appendConsoleLine("Pip is already installed. Verifying numpy/pandas installation...");
                verifyNumpyPandas(() -> {
                    if (pendingWheelFile != null) {
                        runOnUiThread(() -> appendConsoleLine("Installing droidrun with Google Gemini support..."));
                        installWheelWithPip(pendingWheelFile);
                    }
                });
            } else {
                // Pip is not installed, install it first
                Logger.logInfo(LOG_TAG, "Pip is not installed. Installing pip and build tools globally...");
                appendConsoleLine("Pip is not installed. Installing pip and build tools globally...");
                installPipGlobally();
            }
        } else if (pipInstallInProgress) {
            // This is a pip installation result
            Logger.logInfo(LOG_TAG, "Pip installation result - exitCode: " + exitCode);
            Logger.logVerbose(LOG_TAG, "Pip installation stdout: " + stdout.substring(0, Math.min(500, stdout.length())));
            if (!stderr.isEmpty()) {
                Logger.logError(LOG_TAG, "Pip installation stderr: " + stderr.substring(0, Math.min(500, stderr.length())));
            }
            pipInstallInProgress = false;
            if (exitCode == 0) {
                Logger.logInfo(LOG_TAG, "Pip and build tools installed successfully. Verifying numpy/pandas installation...");
                appendConsoleLine("Pip and build tools installed successfully. Verifying numpy/pandas installation...");
                // Check if numpy/pandas are installed from Termux packages, if not pip will try to build them
                verifyNumpyPandas(() -> {
                    if (pendingWheelFile != null) {
                        runOnUiThread(() -> appendConsoleLine("Installing droidrun with Google Gemini support..."));
                        installWheelWithPip(pendingWheelFile);
                    }
                });
            } else {
                String errorMsg = "Failed to install pip/build tools with exit code " + exitCode;
                if (!stderr.isEmpty()) {
                    errorMsg += ": " + stderr;
                    Logger.logError(LOG_TAG, "Full pip installation stderr: " + stderr);
                }
                Logger.logError(LOG_TAG, errorMsg);
                appendConsoleLine("ERROR: " + errorMsg);
                statusView.setText("Error: " + errorMsg);
                if (installLunarButton != null) {
                    installLunarButton.setEnabled(true);
                }
                installLunarInProgress = false;
                pendingWheelFile = null;
            }
        } else if (numpyPandasCheckInProgress) {
            // This is a numpy/pandas check result
            Logger.logInfo(LOG_TAG, "Numpy/pandas check result - exitCode: " + exitCode);
            numpyPandasCheckInProgress = false;
            if (exitCode == 0 && !stdout.contains("NUMPY_PANDAS_NOT_FOUND") && !stderr.contains("No module named")) {
                Logger.logInfo(LOG_TAG, "Numpy and pandas are available. Proceeding with droidrun installation...");
                appendConsoleLine("Numpy and pandas are available. Proceeding with droidrun installation...");
                if (numpyPandasCheckCallback != null) {
                    numpyPandasCheckCallback.run();
                    numpyPandasCheckCallback = null;
                }
            } else {
                Logger.logWarn(LOG_TAG, "Numpy/pandas not found, but proceeding anyway. Pip will try to install them.");
                appendConsoleLine("Numpy/pandas not found, pip will install them if needed...");
                if (numpyPandasCheckCallback != null) {
                    numpyPandasCheckCallback.run();
                    numpyPandasCheckCallback = null;
                }
            }
        } else if (verificationInProgress) {
            // This is a verification result
            verificationInProgress = false;
            if (exitCode == 0 && stdout.contains("droidrun installed successfully")) {
                appendConsoleLine("Verification successful! droidrun is installed correctly.");
                statusView.setText("Lunar Agent installed successfully!");
                if (installLunarButton != null) {
                    installLunarButton.setEnabled(true);
                }
                installLunarInProgress = false;
                pendingWheelFile = null;
            } else {
                String errorMsg = "Verification failed. droidrun may not be installed correctly.";
                if (!stderr.isEmpty()) {
                    errorMsg += " Error: " + stderr;
                }
                Logger.logError(LOG_TAG, errorMsg);
                statusView.setText("Error: " + errorMsg);
                if (installLunarButton != null) {
                    installLunarButton.setEnabled(true);
                }
                installLunarInProgress = false;
            }
        } else {
            // This is a pip install result
            if (exitCode == 0) {
                appendConsoleLine("Pip install completed successfully. Verifying installation...");
                verificationInProgress = true;
                verifyInstallation();
            } else {
                String errorMsg = "Pip install failed with exit code " + exitCode;
                if (!stderr.isEmpty()) {
                    errorMsg += ": " + stderr;
                }
                Logger.logError(LOG_TAG, errorMsg);
                statusView.setText("Error: " + errorMsg);
                if (installLunarButton != null) {
                    installLunarButton.setEnabled(true);
                }
                installLunarInProgress = false;
            }
        }
    }

    /**
     * Verifies that numpy and pandas are available (either from Termux packages or pip).
     * This helps avoid building them from source during droidrun installation.
     */
    private void verifyNumpyPandas(Runnable onComplete) {
        numpyPandasCheckInProgress = true;
        numpyPandasCheckCallback = onComplete;
        String checkCommand = "python -c \"import numpy; import pandas; print('numpy and pandas available')\" 2>&1 || echo 'NUMPY_PANDAS_NOT_FOUND'";
        
        Logger.logInfo(LOG_TAG, "Verifying numpy/pandas with command: " + checkCommand);
        runOnUiThread(() -> appendConsoleLine("Checking if numpy and pandas are available..."));
        
        Uri executableUri = new Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
            .build();

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri);
        execIntent.setClass(this, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", checkCommand});
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.getMode());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Check numpy/pandas");
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createNumpyPandasCheckPendingIntent());
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, String.valueOf(Logger.LOG_LEVEL_VERBOSE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }
    }

    /**
     * Creates a PendingIntent for numpy/pandas check results.
     */
    private PendingIntent createNumpyPandasCheckPendingIntent() {
        Intent resultIntent = new Intent(ACTION_INSTALL_LUNAR_RESULT).setPackage(getPackageName());
        int requestCode = INSTALL_LUNAR_REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }

    /**
     * Verifies that droidrun was installed successfully by importing it.
     */
    private void verifyInstallation() {
        String command = "python -c \"import droidrun; print('droidrun installed successfully')\"";
        
        Logger.logInfo(LOG_TAG, "Verifying droidrun installation with command: " + command);
        appendConsoleLine("Verifying installation: " + command);
        
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
        Intent resultIntent = new Intent(ACTION_INSTALL_LUNAR_RESULT).setPackage(getPackageName());
        int requestCode = INSTALL_LUNAR_REQUEST_CODES.incrementAndGet();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }
}

