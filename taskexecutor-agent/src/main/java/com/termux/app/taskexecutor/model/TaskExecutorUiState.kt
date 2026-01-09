package com.termux.app.taskexecutor.model

import com.termux.app.taskexecutor.model.TaskStatus

/**
 * UI State for Task Executor
 */
data class TaskExecutorUiState(
    val statusText: String = "",
    val outputText: String = "",
    val isUiEnabled: Boolean = false,
    val sessionFinished: Boolean = false,
    val exitCode: Int? = null,
    val currentTask: String? = null,
    val taskProgress: Int = 0,
    val isTaskRunning: Boolean = false,
    val showLogs: Boolean = false,
    val taskStatus: TaskStatus = TaskStatus.STOPPED,
    val agentStateMessage: String = "Ready",
    val maxSteps: Int = 0,
    val chatMessages: List<ChatMessage> = emptyList()
)

/**
 * Represents a message in the chat interface.
 */
data class ChatMessage(
    val text: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Type of chat message.
 */
enum class MessageType {
    USER,      // User command
    SYSTEM,    // System status/progress messages
    OUTPUT,    // Command output
    ERROR      // Error messages
}

