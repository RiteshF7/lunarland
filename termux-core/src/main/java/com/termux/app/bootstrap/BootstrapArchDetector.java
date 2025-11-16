package com.termux.app.bootstrap;

import android.os.Build;
import com.termux.shared.logger.Logger;

/**
 * Detects the CPU architecture for bootstrap selection.
 */
public class BootstrapArchDetector {
    private static final String LOG_TAG = "BootstrapArchDetector";

    /**
     * Detects the current device architecture and returns the corresponding bootstrap arch name.
     * @return Architecture string: "aarch64", "arm", "i686", or "x86_64"
     */
    public static String detectArch() {
        String arch = Build.SUPPORTED_ABIS[0];
        String result;

        if (arch.contains("arm64-v8a") || arch.contains("aarch64")) {
            result = "aarch64";
        } else if (arch.contains("armeabi-v7a") || arch.contains("arm")) {
            result = "arm";
        } else if (arch.contains("x86_64")) {
            result = "x86_64";
        } else if (arch.contains("x86") || arch.contains("i686")) {
            result = "i686";
        } else {
            Logger.logError(LOG_TAG, "Unsupported architecture: " + arch + ", defaulting to aarch64");
            result = "aarch64";
        }

        Logger.logInfo(LOG_TAG, "Detected architecture: " + arch + " -> " + result);
        return result;
    }
}

