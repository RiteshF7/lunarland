package com.termux.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Simple helper that runs commands against the Termux bootstrap binaries without any UI.
 *
 * Usage:
 * ```
 * BootstrapCommandExecutor.executeAsync(context, "ls") { result ->
 *     if (result.success) {
 *         Log.d("TAG", result.stdout)
 *     } else {
 *         Log.e("TAG", result.errorMessage ?: "unknown error")
 *     }
 * }
 * ```
 */
object BootstrapCommandExecutor {

    private const val LOG_TAG = "BootstrapCommandExecutor"

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BootstrapCommandExecutor").apply {
            isDaemon = true
        }
    }

    /**
     * Execute [command] on a background thread and deliver the result on the main thread.
     *
     * @param context Any [Context]. The application context will be used internally.
     * @param command Shell command to execute (for example `"ls"`).
     * @param callback Callback that will receive the [CommandResult].
     */
    @JvmStatic
    fun executeAsync(context: Context, command: String, callback: Callback) {
        executeAsync(context, command, callback, Handler(Looper.getMainLooper()))
    }

    /**
     * Execute [command] on a background thread and deliver the result on [handler].
     *
     * @param context Any [Context]. The application context will be used internally.
     * @param command Shell command to execute (for example `"ls"`).
     * @param callback Callback that will receive the [CommandResult].
     * @param handler Handler used to deliver the callback. If `null` the callback is invoked on the worker thread.
     */
    @JvmStatic
    fun executeAsync(context: Context, command: String, callback: Callback, handler: Handler?) {
        val appContext = context.applicationContext
        executor.execute {
            val result = executeInternal(appContext, command)
            dispatchResult(result, callback, handler)
        }
    }

    /**
     * Execute [command] on the current thread.
     *
     * @param context Any [Context]. The application context will be used internally.
     * @param command Shell command to execute.
     * @return A [CommandResult] containing stdout, stderr and exit status.
     */
    @JvmStatic
    fun executeSync(context: Context, command: String): CommandResult {
        return executeInternal(context.applicationContext, command)
    }

    private fun dispatchResult(result: CommandResult, callback: Callback, handler: Handler?) {
        if (handler == null) {
            callback.onResult(result)
        } else {
            handler.post { callback.onResult(result) }
        }
    }

    private fun executeInternal(context: Context, command: String): CommandResult {
        if (command.isEmpty()) {
            val message = "Command must not be empty."
            Logger.logError(LOG_TAG, message)
            return CommandResult.failure(-1, "", "", message, null)
        }

        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) {
            val message = "Command must not be empty."
            Logger.logError(LOG_TAG, message)
            return CommandResult.failure(-1, "", "", message, null)
        }

        val prefixError = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(false, false)
        if (prefixError != null) {
            val message = "Termux bootstrap is missing. ${Error.getMinimalErrorString(prefixError)}"
            Logger.logError(LOG_TAG, message)
            return CommandResult.failure(-1, "", "", message, null)
        }

        val shellBinary = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "sh")
        if (!shellBinary.exists() || !shellBinary.canExecute()) {
            val message = "Bootstrap shell binary is not available at: ${shellBinary.absolutePath}"
            Logger.logError(LOG_TAG, message)
            return CommandResult.failure(-1, "", "", message, null)
        }

        TermuxShellEnvironment.init(context)
        val environment = TermuxShellEnvironment()
        val envMap = HashMap(environment.getEnvironment(context, false))

        val processBuilder = ProcessBuilder(shellBinary.absolutePath, "-c", trimmedCommand)
        val defaultWorkingDirectory = File(environment.defaultWorkingDirectoryPath)
        val workingDir: File = if (defaultWorkingDirectory.isDirectory) {
            defaultWorkingDirectory
        } else {
            File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        }
        processBuilder.directory(workingDir)

        val processEnvironment = processBuilder.environment()
        processEnvironment.clear()
        envMap.forEach { (key, value) ->
            if (value != null) {
                processEnvironment[key] = value
            } else {
                processEnvironment.remove(key)
            }
        }

        Logger.logInfo(LOG_TAG, "Executing command: $trimmedCommand")

        val process = try {
            processBuilder.start()
        } catch (ioException: IOException) {
            val message = "Failed to start process: ${ioException.message}"
            Logger.logStackTraceWithMessage(LOG_TAG, message, ioException)
            return CommandResult.failure(-1, "", "", message, ioException)
        }

        val streamExecutor = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "BootstrapCommandExecutor-Stream").apply {
                isDaemon = true
            }
        }

        val stdoutFuture = streamExecutor.submit(StreamCollector(process.inputStream))
        val stderrFuture = streamExecutor.submit(StreamCollector(process.errorStream))

        val exitCode = try {
            process.waitFor()
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
            val message = "Command execution was interrupted."
            Logger.logStackTraceWithMessage(LOG_TAG, message, interruptedException)
            process.destroyForcibly()
            streamExecutor.shutdownNow()
            return CommandResult.failure(-1, "", "", message, interruptedException)
        }

        val stdout = consumeFuture(stdoutFuture)
        val stderr = consumeFuture(stderrFuture)
        streamExecutor.shutdown()

        val success = exitCode == 0
        Logger.logInfo(LOG_TAG, "Command finished with exit code: $exitCode")
        val errorMessage = if (success) null else "Process exited with code $exitCode"
        return CommandResult(success, exitCode, stdout, stderr, errorMessage, null)
    }

    private fun consumeFuture(future: Future<String>): String {
        return try {
            future.get()
        } catch (exception: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to collect process output", exception)
            ""
        }
    }

    private class StreamCollector(private val inputStream: InputStream) : Callable<String> {
        override fun call(): String {
            inputStream.use { stream ->
                ByteArrayOutputStream().use { buffer ->
                    val chunk = ByteArray(4096)
                    while (true) {
                        val read = stream.read(chunk)
                        if (read == -1) break
                        buffer.write(chunk, 0, read)
                    }
                    return buffer.toString(StandardCharsets.UTF_8.name())
                }
            }
        }
    }

    fun interface Callback {
        fun onResult(result: CommandResult)
    }

    data class CommandResult(
        val success: Boolean,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val errorMessage: String?,
        val exception: Throwable?
    ) {
        companion object {
            @JvmStatic
            fun failure(
                exitCode: Int,
                stdout: String,
                stderr: String,
                errorMessage: String?,
                exception: Throwable?
            ): CommandResult {
                return CommandResult(false, exitCode, stdout, stderr, errorMessage, exception)
            }
        }
    }
}


