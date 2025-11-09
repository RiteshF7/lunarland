package com.termux.app

import java.io.File

/**
 * Minimal file utilities required by [BootstrapCommandExecutor].
 */
object TermuxFileUtils {

    fun isTermuxPrefixDirectoryAccessible(
        createDirectoryIfMissing: Boolean,
        @Suppress("UNUSED_PARAMETER") setMissingPermissions: Boolean
    ): Error? {
        val prefixDir = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)

        if (!prefixDir.exists()) {
            if (createDirectoryIfMissing) {
                if (!prefixDir.mkdirs()) {
                    return Error(message = "Failed to create termux prefix directory at ${prefixDir.absolutePath}")
                }
            } else {
                return Error(message = "Termux prefix directory not found at ${prefixDir.absolutePath}")
            }
        }

        if (!prefixDir.isDirectory) {
            return Error(message = "Termux prefix path is not a directory: ${prefixDir.absolutePath}")
        }

        if (!prefixDir.canRead() || !prefixDir.canExecute()) {
            return Error(message = "Insufficient access to termux prefix directory: ${prefixDir.absolutePath}")
        }

        return null
    }
}


