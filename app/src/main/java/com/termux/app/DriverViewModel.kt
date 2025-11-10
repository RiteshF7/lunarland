package com.termux.app

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.annotation.NonNull
import androidx.lifecycle.AndroidViewModel
import com.termux.R
import com.termux.shared.errors.Errno
import com.termux.shared.logger.Logger
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE

class DriverViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ACTION_DRIVER_RESULT = "com.termux.app.action.DRIVER_RESULT"
        private const val OUTPUT_PREVIEW_LIMIT = 400
        private val REQUEST_CODES = AtomicInteger()
        private const val LOG_TAG = "DriverActivity"
        private val LOGCAT_COMMAND_TAG = Logger.getDefaultLogTag() + "Command"
    }

    private val _statusText = MutableStateFlow(application.getString(R.string.driver_status_bootstrapping))
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _executeEnabled = MutableStateFlow(false)
    val executeEnabled: StateFlow<Boolean> = _executeEnabled.asStateFlow()

    private val _logsText = MutableStateFlow("")
    val logsText: StateFlow<String> = _logsText.asStateFlow()

    private val _events = MutableSharedFlow<CommandEventType>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val logBuffer = StringBuilder()

    private var bootstrapReady = false
    private var bootstrapInProgress = false
    private var logcatWatcher: LogcatWatcher? = null
    private var activeCommand: String? = null

    fun executeCommand(activity: Activity, rawCommand: String) {
        if (!bootstrapReady) {
            setStatus(getApplication<Application>().getString(R.string.driver_status_bootstrapping))
            ensureBootstrapSetup(activity)
            return
        }

        if (TextUtils.isEmpty(rawCommand)) {
            setStatus(getApplication<Application>().getString(R.string.driver_error_empty_command))
            return
        }

        resetLogs()
        val command = adjustCommand(rawCommand.trim())
        activeCommand = command
        appendConsoleLine("Executing: $command")

        val executableUri = Uri.Builder()
            .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/sh")
            .build()

        val execIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri)
        execIntent.setClass(activity, TermuxService::class.java)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.name)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arrayOf("-c", command))
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ExecutionCommand.ShellCreateMode.ALWAYS.mode)
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, activity.getString(R.string.driver_command_label, command))
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, createResultPendingIntent(activity))
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, Logger.LOG_LEVEL_VERBOSE.toString())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            androidx.core.content.ContextCompat.startForegroundService(activity, execIntent)
        } else {
            activity.startService(execIntent)
        }

        startLogcatWatcher(command)
        setStatus(getApplication<Application>().getString(R.string.driver_status_executing, command))
        setExecuteButtonEnabled(false)
        emitEvent(CommandEventType.CLEAR_COMMAND_INPUT)
    }

    fun ensureBootstrapSetup(activity: Activity) {
        if (bootstrapReady || bootstrapInProgress) return

        bootstrapInProgress = true
        setStatus(getApplication<Application>().getString(R.string.driver_status_bootstrapping))
        setExecuteButtonEnabled(false)

        TermuxInstaller.setupBootstrapIfNeeded(activity) {
            activity.runOnUiThread {
                bootstrapInProgress = false
                bootstrapReady = true
                setExecuteButtonEnabled(true)
                setStatus(getApplication<Application>().getString(R.string.driver_status_ready))
            }
        }
    }

    fun handleCommandResult(extras: BundleExtras) {
        stopLogcatWatcher()

        val stdout = extras.stdout.trim()
        val stderr = extras.stderr.trim()
        val errmsg = extras.errmsg.trim()

        if (stdout.isNotEmpty()) {
            appendConsoleLine("=== stdout ===")
            appendConsoleLine(stdout)
        }
        if (stderr.isNotEmpty()) {
            appendConsoleLine("=== stderr ===")
            appendConsoleLine(stderr)
        }

        if (extras.exitCode == 0) {
            setStatus(getApplication<Application>().getString(R.string.driver_status_done))
        } else {
            val message = when {
                errmsg.isNotEmpty() -> errmsg
                stderr.isNotEmpty() -> stderr.take(OUTPUT_PREVIEW_LIMIT)
                else -> ""
            }
            val displayMessage = if (message.isEmpty()) "Unknown error" else message
            setStatus(getApplication<Application>().getString(R.string.driver_status_error, extras.errCode, displayMessage))
            Logger.logError(LOG_TAG, "Command failed (err ${extras.errCode}): $displayMessage")
        }
    }

    fun handleMissingResult() {
        bootstrapReady = true
        bootstrapInProgress = false
        setExecuteButtonEnabled(true)
        setStatus(getApplication<Application>().getString(R.string.driver_status_result_missing))
        stopLogcatWatcher()
        Logger.logWarn(LOG_TAG, "Command finished but no result bundle was returned")
    }

    fun stopLogcatWatcher() {
        logcatWatcher?.stopWatching()
        logcatWatcher = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLogcatWatcher()
    }

    private fun emitEvent(type: CommandEventType) {
        _events.tryEmit(type)
    }

    private fun setStatus(message: String) {
        _statusText.value = message
    }

    private fun setExecuteButtonEnabled(enabled: Boolean) {
        _executeEnabled.value = enabled
    }

    private fun createResultPendingIntent(activity: Activity): PendingIntent {
        val resultIntent = Intent(ACTION_DRIVER_RESULT).setPackage(activity.packageName)
        val requestCode = REQUEST_CODES.incrementAndGet()
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        flags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31 -> flags or PendingIntent.FLAG_IMMUTABLE
            Build.VERSION.SDK_INT >= 31 -> flags or 0x02000000 // PendingIntent.FLAG_MUTABLE
            else -> flags
        }
        return PendingIntent.getBroadcast(activity, requestCode, resultIntent, flags)
    }

    private fun adjustCommand(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("logcat")) trimmed else "logcat -d | head -n 50 && echo Executing command: $trimmed"
    }

    private fun startLogcatWatcher(command: String) {
        logcatWatcher?.stopWatching()
        logcatWatcher = LogcatWatcher(command, object : LogcatWatcher.Callback {
            override fun onLine(line: String) {
                val out = line.trim()
                if (out.isNotEmpty()) appendConsoleLine(out)
            }

            override fun onError(message: String) {
                setStatus(getApplication<Application>().getString(R.string.driver_status_error, -1, message))
            }
        })
        logcatWatcher?.start()
    }

    private fun resetLogs() {
        synchronized(logBuffer) { logBuffer.setLength(0) }
        _logsText.value = ""
    }

    private fun appendConsoleLine(line: String) {
        if (TextUtils.isEmpty(line)) return
        val snapshot: String
        synchronized(logBuffer) {
            if (logBuffer.isNotEmpty()) logBuffer.append('\n')
            logBuffer.append(line)
            snapshot = logBuffer.toString()
        }
        _logsText.value = snapshot
    }

    enum class CommandEventType {
        CLEAR_COMMAND_INPUT
    }

    class BundleExtras(
        val stdout: String,
        val stderr: String,
        val errmsg: String,
        val exitCode: Int,
        val errCode: Int
    ) {
        companion object {
            fun from(intent: Intent?): BundleExtras? {
                if (intent == null) return null
                val bundle = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE) ?: return null
                val stdout = getString(bundle, TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT)
                val stderr = getString(bundle, TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR)
                val errmsg = getString(bundle, TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG)
                val exitCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, 0)
                val errCode = bundle.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERR, Errno.ERRNO_FAILED.code)
                return BundleExtras(stdout, stderr, errmsg, exitCode, errCode)
            }

            private fun getString(bundle: android.os.Bundle, key: String): String {
                return bundle.getString(key, "") ?: ""
            }
        }
    }

    private class LogcatWatcher(
        private val commandTrigger: String,
        private val callback: Callback?
    ) {
        interface Callback {
            fun onLine(line: String)
            fun onError(message: String)
        }

        private var process: Process? = null
        private var thread: Thread? = null
        @Volatile private var running: Boolean = false
        private var capturing: Boolean = false

        fun start() {
            running = true
            thread = Thread({
                try {
                    val builder = ProcessBuilder(
                        "logcat",
                        "--pid=" + android.os.Process.myPid(),
                        "-v", "brief"
                    )
                    process = builder.start()
                    BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                        var line: String?
                        while (running) {
                            line = reader.readLine()
                            if (line == null) break
                            handleLine(line!!)
                        }
                    }
                } catch (e: IOException) {
                    callback?.onError("Logcat watcher error: ${e.message}")
                }
            }, "LogcatWatcher")
            thread!!.start()
        }

        fun stopWatching() {
            running = false
            process?.destroy()
            thread?.let {
                try { it.join(200) } catch (_: InterruptedException) {}
            }
        }

        private fun handleLine(raw: String) {
            if (TextUtils.isEmpty(raw)) return
            if (!capturing) {
                if (raw.contains(LOG_TAG) && raw.contains("Executing command: $commandTrigger")) {
                    capturing = true
                } else {
                    return
                }
            }

            val sanitized = sanitize(raw)
            if (sanitized != null) callback?.onLine(sanitized)
        }

        private fun sanitize(raw: String): String? {
            // Basic cleanup similar to original intent
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return trimmed.replace(LOGCAT_COMMAND_TAG, "")
        }
    }
}