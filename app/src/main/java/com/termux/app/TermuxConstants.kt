package com.termux.app

/**
 * Minimal constants required by [BootstrapCommandExecutor].
 */
object TermuxConstants {
    const val TERMUX_PREFIX_DIR_PATH: String = "/data/data/com.termux/files/usr"
    const val TERMUX_BIN_PREFIX_DIR_PATH: String = "$TERMUX_PREFIX_DIR_PATH/bin"
    const val TERMUX_HOME_DIR_PATH: String = "/data/data/com.termux/files/home"
    const val TERMUX_TMP_PREFIX_DIR_PATH: String = "$TERMUX_PREFIX_DIR_PATH/tmp"
}


