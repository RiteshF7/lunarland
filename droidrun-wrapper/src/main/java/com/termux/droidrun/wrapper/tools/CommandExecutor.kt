package com.termux.droidrun.wrapper.tools

/**
 * Interface for executing commands (e.g., via Termux)
 * This will be implemented by the actual command execution system
 */
interface CommandExecutor {
    /**
     * Execute a shell command
     * @param command The command to execute
     * @param args List of command arguments
     * @return CommandResult with exit code, stdout, stderr, and success status
     */
    suspend fun execute(command: String, args: List<String> = emptyList()): CommandResult
    
    /**
     * Execute a Python script
     * @param script The Python script content or path
     * @param args Map of arguments/environment variables to pass to the script
     * @return CommandResult with exit code, stdout, stderr, and success status
     */
    suspend fun executePython(script: String, args: Map<String, String> = emptyMap()): CommandResult
}

/**
 * Result of a command execution
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val success: Boolean
) {
    companion object {
        fun success(stdout: String = "", exitCode: Int = 0): CommandResult {
            return CommandResult(exitCode, stdout, "", true)
        }
        
        fun failure(stderr: String = "", exitCode: Int = 1): CommandResult {
            return CommandResult(exitCode, "", stderr, false)
        }
    }
}

