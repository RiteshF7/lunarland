package com.termux.app;

import android.app.Application;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.termux.shared.errors.Error;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.theme.TermuxThemeUtils;

import java.util.Objects;

/**
 * Entry point for initializing Termux core services from the host application.
 */
public final class TermuxCore {

    private static final String LOG_TAG = "TermuxCore";

    private static volatile boolean sInitialized;

    private TermuxCore() {
        // Utility class.
    }

    /**
     * Initialize the Termux core runtime. Must be called exactly once from the host application's
     * {@link Application#onCreate()} before any other Termux components are used.
     */
    @MainThread
    public static synchronized void initialize(@NonNull Application application) {
        initialize(application, TermuxCoreConfig.createDefault());
    }

    /**
     * Initialize the Termux core runtime with the supplied configuration.
     */
    @MainThread
    public static synchronized void initialize(@NonNull Application application,
                                               @NonNull TermuxCoreConfig config) {
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (sInitialized) {
            Logger.logDebug(LOG_TAG, "TermuxCore.initialize() called more than once; ignoring");
            return;
        }

        Context context = application.getApplicationContext();

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(application);

        // Set log config for the app
        setLogConfig(context);

        Logger.logDebug(LOG_TAG, "Starting Termux core initialization");

        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        TermuxBootstrap.setTermuxPackageManagerAndVariant(config.getTermuxPackageVariant());

        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);

        // Init app wide shell manager
        TermuxShellManager.init(context);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode());

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(application, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible");

            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
                return;
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(application);

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(application);
        }

        sInitialized = true;
    }

    public static void setLogConfig(@NonNull Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }
}
