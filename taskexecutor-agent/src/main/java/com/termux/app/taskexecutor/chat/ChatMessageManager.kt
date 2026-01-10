package com.termux.app.taskexecutor.chat

import com.termux.app.taskexecutor.model.ChatMessage
import com.termux.app.taskexecutor.model.MessageType
import com.termux.app.taskexecutor.model.TaskStatus
import com.termux.shared.logger.Logger

/**
 * Manages chat messages and log processing for Task Executor.
 * Handles message creation, filtering, and user-friendly message extraction.
 */
class ChatMessageManager {
    
    private val LOG_TAG = "ChatMessageManager"
    
    // Track previous state to detect changes
    private var lastOutputText: String = ""
    private var lastStatusText: String = ""
    private var lastCurrentTask: String? = null
    private var lastTaskStatus: TaskStatus? = null
    private var lastIsTaskRunning: Boolean = false
    
    // Google API key patterns to filter out
    private val googleApiKeyPatterns = listOf(
        "google_api_key", "goog_api_key", "api_key", "api key",
        "exported from local.properties", "already set in environment",
        "export google_api_key", "goog_api_key exported",
        "goog_api_key already", "goog_api_key is set",
        "goog_api_key loaded", "goog_api_key not found",
        "goog_api_key length", "setting up goog_api_key"
    )
    
    // User-friendly keywords
    private val userFriendlyKeywords = listOf(
        "making request", "getting answer", "executing action", "making decision",
        "goal achieved", "goal succeeded", "task completed", "successfully completed",
        "starting task", "processing", "analyzing", "thinking", "planning",
        "deciding", "executing", "completed", "succeeded", "failed", "error occurred",
        "task stopped", "ready", "setting up", "goal failed", "task execution",
        "code execution", "action completed", "decision made", "request sent",
        "response received", "analyzing task", "planning steps", "executing step"
    )
    
    /**
     * Process output changes and generate chat messages.
     */
    fun processOutputChange(
        currentOutput: String,
        existingMessages: List<ChatMessage>
    ): List<ChatMessage> {
        if (currentOutput == lastOutputText) {
            return existingMessages
        }
        
        val newOutput = when {
            lastOutputText.isBlank() -> currentOutput
            currentOutput.length > lastOutputText.length && 
            currentOutput.startsWith(lastOutputText) -> {
                currentOutput.substring(lastOutputText.length)
            }
            else -> currentOutput
        }
        
        val messages = existingMessages.toMutableList()
        
        if (newOutput.isNotBlank()) {
            val trimmedOutput = newOutput.trim()
            if (trimmedOutput.isNotBlank()) {
                val lowerOutput = trimmedOutput.lowercase()
                
                // Check if this is a Google API key message
                val isGoogleApiKeyMessage = googleApiKeyPatterns.any { pattern ->
                    lowerOutput.contains(pattern, ignoreCase = true)
                }
                
                if (isGoogleApiKeyMessage) {
                    // Filter out Google API key messages - they're handled separately
                    // Don't add anything, just skip this output
                } else {
                    // Try to extract user-friendly messages from output
                    val friendlyMessages = extractUserFriendlyMessages(trimmedOutput)
                    
                    if (friendlyMessages.isNotEmpty()) {
                        // Add each friendly message as a separate system message
                        friendlyMessages.forEach { friendlyMsg ->
                            messages.add(ChatMessage(
                                text = friendlyMsg,
                                type = MessageType.SYSTEM
                            ))
                        }
                    } else {
                        // If no friendly messages found, add as output (will be filtered if not friendly)
                        messages.add(ChatMessage(
                            text = trimmedOutput,
                            type = MessageType.OUTPUT
                        ))
                    }
                }
            }
        }
        
        lastOutputText = currentOutput
        return messages
    }
    
    /**
     * Process status changes and generate chat messages.
     */
    fun processStatusChange(
        statusText: String,
        maxSteps: Int,
        isTaskRunning: Boolean,
        existingMessages: List<ChatMessage>
    ): List<ChatMessage> {
        if (statusText == lastStatusText || statusText.isBlank() || statusText == "Ready") {
            return existingMessages
        }
        
        val messages = existingMessages.toMutableList()
        val statusMessage = if (isTaskRunning && maxSteps > 0) {
            "$statusText [Max steps: $maxSteps]"
        } else {
            statusText
        }
        
        messages.add(ChatMessage(
            text = statusMessage,
            type = MessageType.SYSTEM
        ))
        
        lastStatusText = statusText
        return messages
    }
    
    /**
     * Process current task changes and generate chat messages.
     */
    fun processTaskChange(
        currentTask: String?,
        maxSteps: Int,
        existingMessages: List<ChatMessage>
    ): List<ChatMessage> {
        if (currentTask == lastCurrentTask) {
            return existingMessages
        }
        
        val messages = existingMessages.toMutableList()
        
        if (currentTask != null && currentTask != lastCurrentTask) {
            val maxStepsText = if (maxSteps > 0) {
                " (Max steps: $maxSteps)"
            } else {
                ""
            }
            messages.add(ChatMessage(
                text = "Executing: $currentTask$maxStepsText",
                type = MessageType.SYSTEM
            ))
        }
        
        lastCurrentTask = currentTask
        return messages
    }
    
    /**
     * Process task status changes and generate chat messages.
     */
    fun processTaskStatusChange(
        taskStatus: TaskStatus,
        isTaskRunning: Boolean,
        existingMessages: List<ChatMessage>
    ): List<ChatMessage> {
        // Only process if status or running state changed
        if (taskStatus == lastTaskStatus && isTaskRunning == lastIsTaskRunning) {
            return existingMessages
        }
        
        val messages = existingMessages.toMutableList()
        
        if (!isTaskRunning) {
            when (taskStatus) {
                TaskStatus.SUCCESS -> {
                    messages.add(ChatMessage(
                        text = "✓ Task completed successfully",
                        type = MessageType.SYSTEM
                    ))
                }
                TaskStatus.ERROR -> {
                    messages.add(ChatMessage(
                        text = "✗ Task failed or error occurred",
                        type = MessageType.ERROR
                    ))
                }
                TaskStatus.STOPPED -> {
                    messages.add(ChatMessage(
                        text = "Task stopped",
                        type = MessageType.SYSTEM
                    ))
                }
                else -> {}
            }
        }
        
        lastTaskStatus = taskStatus
        lastIsTaskRunning = isTaskRunning
        return messages
    }
    
    /**
     * Add a user message.
     */
    fun addUserMessage(
        command: String,
        existingMessages: List<ChatMessage>
    ): List<ChatMessage> {
        return existingMessages + ChatMessage(
            text = command,
            type = MessageType.USER
        )
    }
    
    /**
     * Add a system message.
     */
    fun addSystemMessage(
        text: String,
        existingMessages: List<ChatMessage>
    ): List<ChatMessage> {
        // Avoid duplicates - check if the same message was just added
        val lastMessage = existingMessages.lastOrNull()
        if (lastMessage?.text == text && lastMessage.type == MessageType.SYSTEM) {
            return existingMessages
        }
        return existingMessages + ChatMessage(
            text = text,
            type = MessageType.SYSTEM
        )
    }
    
    /**
     * Extract user-friendly messages from technical output.
     */
    private fun extractUserFriendlyMessages(output: String): List<String> {
        val messages = mutableListOf<String>()
        val lines = output.split("\n", "\r\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            
            val lowerLine = trimmed.lowercase()
            
            // Skip droidrun-related logs
            if (lowerLine.contains("droidrun", ignoreCase = true)) {
                continue
            }
            
            // Skip Google API key logs
            if (googleApiKeyPatterns.any { pattern ->
                lowerLine.contains(pattern, ignoreCase = true)
            }) {
                continue
            }
            
            // Check if line contains user-friendly keywords
            val hasFriendlyKeyword = userFriendlyKeywords.any { keyword ->
                lowerLine.contains(keyword, ignoreCase = true)
            }
            
            if (hasFriendlyKeyword) {
                // Extract the meaningful part
                val cleanMessage = cleanMessage(trimmed)
                if (cleanMessage.isNotBlank() && cleanMessage.length > 5) {
                    // Avoid duplicates
                    if (!messages.contains(cleanMessage)) {
                        messages.add(cleanMessage)
                    }
                }
            }
        }
        
        return messages
    }
    
    /**
     * Clean a message to make it more user-friendly.
     */
    private fun cleanMessage(message: String): String {
        var cleaned = message
        
        // Remove log tags
        cleaned = cleaned.replace(Regex("\\[.*?\\]"), "")
        
        // Remove timestamps
        cleaned = cleaned.replace(Regex("\\d{4}-\\d{2}-\\d{2}.*?\\s"), "")
        cleaned = cleaned.replace(Regex("\\d+:\\d+:\\d+.*?\\s"), "")
        
        // Remove file paths
        cleaned = cleaned.replace(Regex("\\/.*?\\/"), "")
        
        // Remove package names at start
        cleaned = cleaned.replace(Regex("^\\w+\\.\\w+\\.\\w+\\s*"), "")
        
        // Capitalize first letter
        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.trim()
            cleaned = cleaned.replaceFirstChar { it.uppercaseChar() }
        }
        
        return cleaned.trim()
    }
    
    /**
     * Reset all tracked state (called when screen is reopened).
     */
    fun reset() {
        lastOutputText = ""
        lastStatusText = ""
        lastCurrentTask = null
        lastTaskStatus = null
        lastIsTaskRunning = false
        Logger.logInfo(LOG_TAG, "Chat message manager state reset")
    }
    
    /**
     * Clear all messages.
     */
    fun clearMessages(): List<ChatMessage> {
        reset()
        return emptyList()
    }
}

