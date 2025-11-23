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
import com.termux.app.bootstrap.WheelsDownloader;

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
    private Button installDroidrunButton;
    private Button runDroidrunButton;
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
        installDroidrunButton = findViewById(R.id.task_executor_install_droidrun_button);
        runDroidrunButton = findViewById(R.id.task_executor_run_droidrun_button);
        statusView = findViewById(R.id.task_executor_status);
        outputView = findViewById(R.id.task_executor_output);
        outputScrollView = findViewById(R.id.task_executor_output_container);

        executeButton.setOnClickListener(v -> dispatchCommand());
        resetButton.setOnClickListener(v -> restartSession());
        installButton.setOnClickListener(v -> runInstallScript());
        initSetupButton.setOnClickListener(v -> runInitSetupScript());
        installDroidrunButton.setOnClickListener(v -> installDroidrunDependencies());
        runDroidrunButton.setOnClickListener(v -> runDroidrunCommand());
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
        installDroidrunButton.setEnabled(enabled);
        runDroidrunButton.setEnabled(enabled);
        commandInput.setEnabled(enabled);
    }

    private void runInstallScript() {
        if (terminalSession == null || sessionFinished) {
            return;
        }

        // Extract script from assets and execute it
        new Thread(() -> {
            try {
                String scriptPath = extractScriptFromAssets("setup_droidrun.sh");
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
                        Logger.logError(LOG_TAG, "Failed to extract DroidRun setup script from assets");
                        statusView.setText("Failed to load DroidRun setup script");
                    });
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error running DroidRun setup script", e);
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

    /**
     * Install Python and droidrun dependencies using the persistent terminal session
     * Downloads pre-built wheels from GitHub releases using WheelsDownloader (same pattern as BootstrapDownloader)
     */
    private void installDroidrunDependencies() {
        if (terminalSession == null || sessionFinished) {
            return;
        }

        // Use Android download mechanism (same as bootstrap) instead of curl
        new Thread(() -> {
            try {
                // First install Python if needed
                String pythonCheckCommand = "if ! command -v python3 &> /dev/null; then pkg install -y python; fi";
                mainHandler.post(() -> {
                    if (terminalSession != null && !sessionFinished) {
                        terminalSession.write("echo '=== Installing Droidrun Dependencies ===' && ");
                        terminalSession.write(pythonCheckCommand);
                        terminalSession.write("\n");
                    }
                });

                // Download wheels using WheelsDownloader (same pattern as BootstrapDownloader)
                mainHandler.post(() -> {
                    if (terminalSession != null && !sessionFinished) {
                        terminalSession.write("echo 'Downloading wheels from GitHub...' && ");
                    }
                });

                com.termux.app.bootstrap.WheelsDownloader.ProgressCallback progressCallback = 
                    (downloaded, total) -> {
                        if (total > 0) {
                            int percent = (int) ((downloaded * 100) / total);
                            mainHandler.post(() -> {
                                if (terminalSession != null && !sessionFinished) {
                                    terminalSession.write(
                                        String.format("echo 'Downloading: %d / %d bytes (%.1f%%)' && ",
                                            downloaded, total, (downloaded * 100.0 / total)));
                                }
                            });
                        }
                    };

                com.termux.shared.errors.Error downloadError = 
                    com.termux.app.bootstrap.WheelsDownloader.downloadWheels(
                        getApplicationContext(),
                        progressCallback
                    );

                if (downloadError != null) {
                    String errorMsg = "Download failed: " + downloadError.getMessage();
                    Logger.logError(LOG_TAG, errorMsg);
                    mainHandler.post(() -> {
                        if (terminalSession != null && !sessionFinished) {
                            terminalSession.write("echo 'ERROR: " + errorMsg + "' && ");
                        }
                    });
                    return;
                }

                // Extract and install wheels
                String wheelsDir = TermuxConstants.TERMUX_HOME_DIR_PATH + "/wheels";
                java.io.File localWheelsFile = com.termux.app.bootstrap.WheelsDownloader.getLocalWheelsFile(getApplicationContext());
                
                String installCommand = 
                    "echo 'Extracting wheels...' && " +
                    "mkdir -p '" + wheelsDir + "' && " +
                    "tar -xzf '" + localWheelsFile.getAbsolutePath() + "' -C '" + wheelsDir + "' && " +
                    "echo 'Wheels extracted successfully' && " +
                    "echo '' && " +
                    "echo 'Installing droidrun from local wheels...' && " +
                    "if ! pip3 show droidrun &> /dev/null; then " +
                    "  pip3 install --no-index --find-links '" + wheelsDir + "' droidrun && " +
                    "  echo 'droidrun installed successfully from local wheels'; " +
                    "else " +
                    "  echo 'droidrun already installed, reinstalling from local wheels...' && " +
                    "  pip3 install --force-reinstall --no-index --find-links '" + wheelsDir + "' droidrun && " +
                    "  echo 'droidrun reinstalled successfully'; " +
                    "fi && " +
                    "echo '' && " +
                    "echo 'Verifying installation...' && " +
                    "pip3 show droidrun | head -5 && " +
                    "echo '' && " +
                    "echo '=== Installation Complete ==='";

                mainHandler.post(() -> {
                    if (terminalSession != null && !sessionFinished) {
                        terminalSession.write(installCommand);
                        terminalSession.write("\n");
                    }
                });

            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error installing droidrun dependencies", e);
                mainHandler.post(() -> {
                    if (terminalSession != null && !sessionFinished) {
                        terminalSession.write("echo 'ERROR: " + e.getMessage() + "' && ");
                    }
                });
            }
        }).start();
    }

    /**
     * Execute droidrun command using the persistent terminal session
     * This will use the droidrun_wrapper.py script from assets
     */
    private void runDroidrunCommand() {
        if (terminalSession == null || sessionFinished) {
            return;
        }

        // First ensure wrapper script is available
        new Thread(() -> {
            try {
                String scriptPath = extractScriptFromAssets("droidrun_wrapper.py");
                if (scriptPath != null) {
                    // Make script executable and prepare droidrun command
                    // The wrapper script will handle the actual droidrun execution
                    String command = 
                        "echo '=== Starting Droidrun ===' && " +
                        "chmod +x '" + scriptPath + "' && " +
                        "echo 'Wrapper script ready: ' && " +
                        "ls -lh '" + scriptPath + "' && " +
                        "echo '' && " +
                        "echo 'Running droidrun wrapper...' && " +
                        "python3 '" + scriptPath + "'";
                    
                    mainHandler.post(() -> {
                        if (terminalSession != null && !sessionFinished) {
                            terminalSession.write(command);
                            terminalSession.write("\n");
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        Logger.logError(LOG_TAG, "Failed to extract droidrun wrapper script from assets");
                        statusView.setText("Failed to load droidrun wrapper script");
                    });
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error running droidrun command", e);
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

