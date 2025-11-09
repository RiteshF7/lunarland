package com.termux.app

import android.util.Log

/**
 * Minimal logger used by [BootstrapCommandExecutor].
 */
object Logger {

    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    fun logStackTraceWithMessage(tag: String, message: String?, throwable: Throwable) {
        val logMessage = buildString {
            if (!message.isNullOrEmpty()) {
                append(message)
                append(": ")
            }
            append(Log.getStackTraceString(throwable))
        }
        Log.e(tag, logMessage)
    }
}


