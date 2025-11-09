package com.termux.app

import android.content.Context
import java.io.File
import java.util.HashMap

/**
 * Minimal Termux shell environment used for executing bootstrap commands without depending on
 * external modules.
 */
class TermuxShellEnvironment {

    fun getEnvironment(context: Context, isFailSafe: Boolean): Map<String, String> {
        val environment = HashMap<String, String>(System.getenv())

        environment[ENV_HOME] = TermuxConstants.TERMUX_HOME_DIR_PATH
        environment[ENV_PREFIX] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
        environment[ENV_TMPDIR] = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH

        if (!isFailSafe) {
            environment[ENV_PATH] = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
            environment.remove(ENV_LD_LIBRARY_PATH)
        }

        return environment
    }

    val defaultWorkingDirectoryPath: String
        get() = TermuxConstants.TERMUX_HOME_DIR_PATH

    companion object {
        private const val ENV_HOME = "HOME"
        private const val ENV_PREFIX = "PREFIX"
        private const val ENV_TMPDIR = "TMPDIR"
        private const val ENV_PATH = "PATH"
        private const val ENV_LD_LIBRARY_PATH = "LD_LIBRARY_PATH"

        @JvmStatic
        fun init(context: Context) {
            TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, false)
            ensureDirectory(TermuxConstants.TERMUX_HOME_DIR_PATH)
            ensureDirectory(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH)
        }

        private fun ensureDirectory(path: String) {
            val directory = File(path)
            if (!directory.exists()) {
                directory.mkdirs()
            }
        }
    }
}


