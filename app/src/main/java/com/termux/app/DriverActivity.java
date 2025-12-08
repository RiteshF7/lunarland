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
    private static final int OUTPUT_PREVIEW_LIMIT = 400;
    private static final AtomicInteger REQUEST_CODES = new AtomicInteger();
    private static final String LOG_TAG = "DriverActivity";
    private static final String LOGCAT_COMMAND_TAG = Logger.getDefaultLogTag() + "Command";

    private EditText commandInput;
    private TextView statusView;
    private Button executeButton;
    private Button launchTermuxButton;
    private Button taskExecutorButton;
    private Button launchLunarHomeScreenButton;
    private Button previewAgentButton;
    private Button testAppDrawerButton;
    private NestedScrollView logsContainer;
    private TextView logsView;
    private final StringBuilder logBuffer = new StringBuilder();

    private boolean bootstrapReady;
    private boolean bootstrapInProgress;
    private boolean receiverRegistered;
    private LogcatWatcher logcatWatcher;
    private String activeCommand;
    
    // ViewModel instance
    private DriverViewModel viewModel;

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                BundleExtras extras = BundleExtras.from(intent);
                runOnUiThread(() -> {
                    if (extras == null) {
                        handleMissingResult();
                    } else {
                        handleCommandResult(extras);
                    }
                });
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
        taskExecutorButton = findViewById(R.id.driver_open_task_executor_button);
        launchLunarHomeScreenButton = findViewById(R.id.driver_launch_lunar_homescreen_button);
        previewAgentButton = findViewById(R.id.driver_preview_agent_button);
        testAppDrawerButton = findViewById(R.id.driver_test_app_drawer_button);
        logsContainer = findViewById(R.id.driver_logs_container);
        logsView = findViewById(R.id.driver_logs_view);

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
                startActivity(new Intent(this, TaskExecutorAgentActivity.class))
            );
        }
        if (launchLunarHomeScreenButton != null) {
            launchLunarHomeScreenButton.setOnClickListener(view -> {
                Intent intent = new Intent(this, LunarHomeScreenActivity.class);
                startActivity(intent);
            });
        }
        if (previewAgentButton != null) {
            previewAgentButton.setOnClickListener(view -> {
                Intent intent = new Intent(this, TaskExecutorAgentActivity.class);
                startActivity(intent);
            });
        }
        if (testAppDrawerButton != null) {
            testAppDrawerButton.setOnClickListener(view -> {
                Intent intent = new Intent(this, AppDrawerActivity.class);
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
}
