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
    val agentStateMessage: String = "Ready"
)

