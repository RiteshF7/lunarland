package com.termux.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.shared.errors.Errno;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;

import java.util.concurrent.atomic.AtomicInteger;

import static com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

/**
 * Minimal activity that bridges user-entered commands to {@link TermuxService}.
 */
public class DriverActivity extends AppCompatActivity {

    private static final String ACTION_DRIVER_RESULT = "com.termux.app.action.DRIVER_RESULT";
    private static final int OUTPUT_PREVIEW_LIMIT = 400;
    private static final AtomicInteger REQUEST_CODES = new AtomicInteger();

    private EditText commandInput;
    private TextView statusView;
    private Button executeButton;

    private boolean bootstrapReady;
    private boolean bootstrapInProgress;
    private boolean receiverRegistered;

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

        commandInput = findViewById(R.id.driver_command_input);
        statusView = findViewById(R.id.driver_status_view);
        executeButton = findViewById(R.id.driver_execute_button);

        statusView.setText(R.string.driver_status_bootstrapping);
        executeButton.setEnabled(false);

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

        String command = rawCommand.trim();

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, execIntent);
        } else {
            startService(execIntent);
        }

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
            flags |= 0x02000000; // PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(this, requestCode, resultIntent, flags);
    }

    private void ensureBootstrapSetup() {
        if (bootstrapReady || bootstrapInProgress) return;
        bootstrapInProgress = true;
        statusView.setText(R.string.driver_status_bootstrapping);
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> runOnUiThread(() -> {
            bootstrapInProgress = false;
            bootstrapReady = true;
            executeButton.setEnabled(true);
            if (TextUtils.isEmpty(commandInput.getText())) {
                statusView.setText(R.string.driver_status_ready);
            }
        }));
    }

    private void handleCommandResult(BundleExtras extras) {
        bootstrapReady = true;
        bootstrapInProgress = false;
        executeButton.setEnabled(true);

        if (extras.errCode == Errno.ERRNO_SUCCESS.getCode()) {
            String output = !extras.stdout.isEmpty() ? extras.stdout : extras.stderr;
            output = trimOutput(output);
            if (output.isEmpty()) {
                statusView.setText(getString(R.string.driver_status_success_no_output, extras.exitCode));
            } else {
                statusView.setText(getString(R.string.driver_status_success, extras.exitCode, output));
            }
        } else {
            String message = !extras.errmsg.isEmpty() ? extras.errmsg : extras.stderr;
            message = trimOutput(message);
            if (!extras.stderr.isEmpty() && !extras.stderr.equals(message)) {
                statusView.setText(getString(R.string.driver_status_error_with_output, extras.errCode, message, trimOutput(extras.stderr)));
            } else {
                statusView.setText(getString(R.string.driver_status_error, extras.errCode, message.isEmpty() ? "Unknown error" : message));
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
        statusView.setText(R.string.driver_status_result_missing);
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
}

