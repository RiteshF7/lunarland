package com.termux.app;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession.TermuxSessionClient;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Activity that provides a minimal UI for executing commands inside a persistent Termux shell session.
 * It keeps the same session alive across multiple commands, which allows directory changes and environment
 * mutations to persist until the user resets the session.
 */
public class TaskExecutorActivity extends AppCompatActivity implements TermuxSessionClient {

    private static final String LOG_TAG = "TaskExecutorActivity";

    private final AtomicInteger sessionIds = new AtomicInteger();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText commandInput;
    private Button executeButton;
    private Button resetButton;
    private Button installButton;
    private Button initSetupButton;
    private TextView statusView;
    private TextView outputView;
    private ScrollView outputScrollView;

    private TermuxSession currentSession;
    private TerminalSession terminalSession;
    private TaskExecutorSessionClient sessionClient;
    private boolean sessionFinished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_executor);

        commandInput = findViewById(R.id.task_executor_command_input);
        executeButton = findViewById(R.id.task_executor_execute_button);
        resetButton = findViewById(R.id.task_executor_reset_button);
        installButton = findViewById(R.id.task_executor_install_button);
        initSetupButton = findViewById(R.id.task_executor_initsetup_button);
        statusView = findViewById(R.id.task_executor_status);
        outputView = findViewById(R.id.task_executor_output);
        outputScrollView = findViewById(R.id.task_executor_output_container);

        executeButton.setOnClickListener(v -> dispatchCommand());
        resetButton.setOnClickListener(v -> restartSession());
        installButton.setOnClickListener(v -> runInstallScript());
        initSetupButton.setOnClickListener(v -> runInitSetupScript());
        commandInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                dispatchCommand();
                return true;
            }
            return false;
        });

        TermuxShellEnvironment.init(getApplicationContext());
        prepareSession();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tearDownSession();
    }

    private void prepareSession() {
        setUiEnabled(false);
        statusView.setText(R.string.driver_status_bootstrapping);
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> runOnUiThread(this::startNewSession));
    }

    private void startNewSession() {
        tearDownSession();
        sessionClient = new TaskExecutorSessionClient();

        ExecutionCommand executionCommand = new ExecutionCommand(sessionIds.incrementAndGet());
        executionCommand.runner = Runner.TERMINAL_SESSION.getName();
        executionCommand.shellName = "taskexecutor";
        executionCommand.commandLabel = "Task Executor Shell";
        executionCommand.workingDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;
        executionCommand.setShellCommandShellEnvironment = true;
        executionCommand.terminalTranscriptRows = 4000;
        executionCommand.shellCreateMode = ExecutionCommand.ShellCreateMode.ALWAYS.getMode();

        currentSession = TermuxSession.execute(
            getApplicationContext(),
            executionCommand,
            sessionClient,
            this,
            new TermuxShellEnvironment(),
            null,
            false
        );

        if (currentSession == null) {
            statusView.setText(getString(R.string.task_executor_status_failed, "Unable to start shell"));
            Logger.logError(LOG_TAG, "Failed to create task executor session");
            resetButton.setEnabled(true);
            return;
        }

        terminalSession = currentSession.getTerminalSession();
        sessionFinished = false;
        try {
            // Use a reasonable default terminal geometry to bootstrap the session.
            terminalSession.initializeEmulator(80, 24, 8, 16);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Unable to initialize terminal emulator", e);
            statusView.setText(getString(R.string.task_executor_status_failed, e.getMessage()));
            resetButton.setEnabled(true);
            return;
        }

        setUiEnabled(true);
        statusView.setText(R.string.task_executor_status_ready);
        sessionClient.refreshTranscript(terminalSession);
    }

    private void dispatchCommand() {
        if (terminalSession == null || sessionFinished) {
            return;
        }

        String command = commandInput.getText().toString();
        if (TextUtils.isEmpty(command)) {
            return;
        }

        terminalSession.write(command);
        terminalSession.write("\n");
        commandInput.setText("");
    }

    private void restartSession() {
        startNewSession();
    }

    private void tearDownSession() {
        if (terminalSession != null) {
            try {
                terminalSession.finishIfRunning();
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to finish existing terminal session", e);
            }
        }
        if (currentSession != null && !sessionFinished) {
            currentSession.finish();
        }
        terminalSession = null;
        currentSession = null;
        sessionFinished = false;
        outputView.setText("");
    }

    private void setUiEnabled(boolean enabled) {
        executeButton.setEnabled(enabled);
        resetButton.setEnabled(true);
        installButton.setEnabled(enabled);
        initSetupButton.setEnabled(enabled);
        commandInput.setEnabled(enabled);
    }

    private void runInstallScript() {
        if (terminalSession == null || sessionFinished) {
            return;
        }

        // Extract script from assets and execute it
        new Thread(() -> {
            try {
                String scriptPath = extractScriptFromAssets("setup_lunar_adb_agent.sh");
                if (scriptPath != null) {
                    // Make script executable and run it with bash
                    String command = "chmod +x '" + scriptPath + "' && bash '" + scriptPath + "'";
                    mainHandler.post(() -> {
                        if (terminalSession != null && !sessionFinished) {
                            terminalSession.write(command);
                            terminalSession.write("\n");
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        Logger.logError(LOG_TAG, "Failed to extract setup script from assets");
                        statusView.setText("Failed to load setup script");
                    });
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error running setup script", e);
                mainHandler.post(() -> {
                    statusView.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private String extractScriptFromAssets(String assetName) {
        AssetManager assetManager = getAssets();
        File scriptFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, assetName);
        
        try {
            // Create parent directory if it doesn't exist
            File parentDir = scriptFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Copy asset to file
            InputStream inputStream = assetManager.open(assetName);
            OutputStream outputStream = new FileOutputStream(scriptFile);
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            outputStream.close();
            inputStream.close();
            
            // Make file executable
            scriptFile.setExecutable(true, false);
            
            Logger.logInfo(LOG_TAG, "Extracted script to: " + scriptFile.getAbsolutePath());
            return scriptFile.getAbsolutePath();
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to extract script from assets", e);
            return null;
        }
    }

    @Override
    public void onTermuxSessionExited(TermuxSession session) {
        Integer exitCode = session.getExecutionCommand().resultData.exitCode;
        runOnUiThread(() -> {
            sessionFinished = true;
            setUiEnabled(false);
            int code = exitCode != null ? exitCode : -1;
            statusView.setText(getString(R.string.task_executor_status_finished, code));
            resetButton.setEnabled(true);
        });
    }

    private void runInitSetupScript() {
        if (terminalSession == null || sessionFinished) {
            return;
        }

        new Thread(() -> {
            try {
                String scriptPath = extractScriptFromAssets("setup_lunar_adb_agent.sh");
                if (scriptPath != null) {
                    // Place script into a bin directory on PATH, ensure executable, then run it.
                    String command =
                        "mkdir -p \"$HOME/bin\" && " +
                        "cp '" + scriptPath + "' \"$HOME/bin/setup_lunar_adb_agent.sh\" && " +
                        "chmod +x \"$HOME/bin/setup_lunar_adb_agent.sh\" && " +
                        "bash \"$HOME/bin/setup_lunar_adb_agent.sh\"";
                    mainHandler.post(() -> {
                        if (terminalSession != null && !sessionFinished) {
                            terminalSession.write(command);
                            terminalSession.write("\n");
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        Logger.logError(LOG_TAG, "Failed to extract Lunar ADB agent setup script from assets");
                        statusView.setText("Failed to load Lunar ADB agent setup script");
                    });
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error running Lunar ADB agent setup script", e);
                mainHandler.post(() -> statusView.setText("Error: " + e.getMessage()));
            }
        }).start();
    }


    private class TaskExecutorSessionClient extends TermuxTerminalSessionClientBase {

        @Override
        public void onTextChanged(@NonNull TerminalSession changedSession) {
            refreshTranscript(changedSession);
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finishedSession) {
            if (currentSession != null && !sessionFinished) {
                currentSession.finish();
            }
        }

        void refreshTranscript(TerminalSession session) {
            if (session == null) return;
            String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, false);
            mainHandler.post(() -> {
                outputView.setText(transcript == null ? "" : transcript);
                if (outputScrollView != null) {
                    outputScrollView.post(() -> outputScrollView.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });
        }
    }
}

