package lunar.land.ui.feature.taskexecagent

/**
 * Utility to filter technical logs and keep only user-friendly messages.
 */
object LogFilter {
    
    /**
     * Keywords that indicate non-technical, user-friendly messages.
     */
    private val userFriendlyKeywords = listOf(
        "making request",
        "getting answer",
        "executing action",
        "making decision",
        "goal achieved",
        "goal succeeded",
        "task completed",
        "successfully completed",
        "starting task",
        "processing",
        "analyzing",
        "thinking",
        "planning",
        "deciding",
        "executing",
        "completed",
        "succeeded",
        "failed",
        "error occurred",
        "task stopped",
        "ready",
        "setting up",
        "goal failed",
        "task execution",
        "code execution",
        "action completed",
        "decision made",
        "request sent",
        "response received",
        "analyzing task",
        "planning steps",
        "executing step"
    )
    
    /**
     * Patterns that indicate technical logs (should be filtered out by default).
     */
    private val technicalPatterns = listOf(
        Regex("\\[.*\\]"),  // Log tags like [DEBUG], [INFO]
        Regex("\\d{4}-\\d{2}-\\d{2}"),  // Dates
        Regex("\\d+:\\d+:\\d+"),  // Timestamps
        Regex("at .*\\(.*\\.(java|kt):\\d+\\)"),  // Stack traces
        Regex("Exception|Error|Throwable"),  // Exception names
        Regex("\\$\\{.*\\}"),  // Variable references
        Regex("\\w+\\.\\w+\\(.*\\)"),  // Method calls
        Regex("\\/.*\\/"),  // File paths
        Regex("\\w+:\\/\\/"),  // URLs
        Regex("exit code|exitcode|exit_code"),  // Exit codes
        Regex("pid|process id"),  // Process IDs
        Regex("\\[\\d+\\]"),  // Process numbers
        Regex("\\w+\\.\\w+\\.\\w+"),  // Package names
        Regex("DEBUG|INFO|WARN|ERROR|TRACE"),  // Log levels
        Regex("\\d+ms|\\d+s|\\d+ms"),  // Time measurements
    )
    
    /**
     * Check if a message is user-friendly (non-technical).
     */
    fun isUserFriendly(message: String): Boolean {
        val lowerMessage = message.lowercase().trim()
        
        // Always show essential completion/failure messages
        if (lowerMessage.contains("task completed successfully") ||
            lowerMessage.contains("task failed") ||
            lowerMessage.contains("task stopped") ||
            lowerMessage.contains("goal achieved") ||
            lowerMessage.contains("goal succeeded") ||
            lowerMessage.contains("goal failed")) {
            return true
        }
        
        // Check for user-friendly keywords (case-insensitive)
        val containsFriendlyKeyword = userFriendlyKeywords.any { keyword ->
            lowerMessage.contains(keyword, ignoreCase = true)
        }
        
        if (containsFriendlyKeyword) {
            // Even if it has a friendly keyword, check if it's too technical
            val hasTechnicalPattern = technicalPatterns.any { pattern ->
                pattern.containsMatchIn(message)
            }
            // If it has friendly keyword but no technical patterns, it's friendly
            if (!hasTechnicalPattern) {
                return true
            }
        }
        
        // Check if it contains technical patterns - if yes, it's technical
        if (technicalPatterns.any { pattern ->
            pattern.containsMatchIn(message)
        }) {
            return false
        }
        
        // Check message length - very long messages are likely technical
        if (message.length > 300) {
            return false
        }
        
        // Check for common technical indicators
        if (lowerMessage.contains("log") && 
            (lowerMessage.contains("debug") || lowerMessage.contains("trace") || 
             lowerMessage.contains("info") || lowerMessage.contains("warn"))) {
            return false
        }
        
        // Check for stack trace indicators
        if (lowerMessage.contains("exception") || 
            lowerMessage.contains("stacktrace") ||
            lowerMessage.contains("at ") && lowerMessage.contains("(")) {
            return false
        }
        
        // Simple, short messages without technical patterns are likely user-friendly
        if (message.split(" ").size <= 15 && message.length < 150 && !containsFriendlyKeyword) {
            // If it's short and doesn't have technical patterns, it might be friendly
            // But be conservative - only show if it looks like a status message
            return lowerMessage.matches(Regex("^[a-z\\s]+$")) && 
                   !lowerMessage.contains(":") &&
                   !lowerMessage.contains("/") &&
                   !lowerMessage.contains("\\")
        }
        
        return false
    }
    
    /**
     * Extract user-friendly messages from technical output.
     * Looks for patterns like "goal achieved", "making request", etc.
     */
    fun extractUserFriendlyMessages(output: String): List<String> {
        val messages = mutableListOf<String>()
        val lines = output.split("\n", "\r\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            
            // Check if line contains user-friendly keywords
            val lowerLine = trimmed.lowercase()
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
     * Check if message should be shown based on filter mode.
     */
    fun shouldShowMessage(message: ChatMessage, showAllLogs: Boolean): Boolean {
        // Always show user messages
        if (message.type == MessageType.USER) {
            return true
        }
        
        // Always show error messages
        if (message.type == MessageType.ERROR) {
            return true
        }
        
        // If showing all logs, show everything
        if (showAllLogs) {
            return true
        }
        
        // For system messages, check if user-friendly
        if (message.type == MessageType.SYSTEM) {
            return isUserFriendly(message.text)
        }
        
        // For output messages, filter technical content
        if (message.type == MessageType.OUTPUT) {
            // First check if the entire message is user-friendly
            if (isUserFriendly(message.text)) {
                return true
            }
            
            // Otherwise, try to extract user-friendly parts
            val friendlyMessages = extractUserFriendlyMessages(message.text)
            return friendlyMessages.isNotEmpty()
        }
        
        return false
    }
}

