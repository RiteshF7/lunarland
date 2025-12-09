package com.termux.app.taskexecutor.state

import com.termux.app.taskexecutor.model.TaskStatus
import com.termux.shared.logger.Logger

/**
 * Centralized state manager for Task Executor agent states.
 * Manages state transitions with validation to ensure consistent state handling.
 */
class TaskExecutorStateManager {
    
    private val LOG_TAG = "TaskExecutorStateManager"
    
    private var currentState: AgentState = AgentState.IDLE
    private var currentTask: String? = null
    private var taskStartTime: Long = 0
    
    /**
     * Agent execution states
     */
    enum class AgentState {
        IDLE,      // No task running, ready for new task
        RUNNING,   // Task is actively executing
        SUCCESS,   // Task completed successfully
        ERROR      // Task failed or error occurred
    }
    
    /**
     * Transition to running state when a task starts
     * Can transition from IDLE, SUCCESS, or ERROR states
     */
    fun transitionToRunning(task: String): StateTransitionResult {
        return when (currentState) {
            AgentState.IDLE, AgentState.SUCCESS, AgentState.ERROR -> {
                Logger.logInfo(LOG_TAG, "Transitioning to RUNNING state for task: $task (from $currentState)")
                currentState = AgentState.RUNNING
                currentTask = task
                taskStartTime = System.currentTimeMillis()
                StateTransitionResult.Success(
                    status = TaskStatus.RUNNING,
                    message = "Running...",
                    isRunning = true
                )
            }
            AgentState.RUNNING -> {
                // If same task, allow it (might be state sync issue)
                if (currentTask == task) {
                    Logger.logDebug(LOG_TAG, "Already running same task: $task")
                    StateTransitionResult.Success(
                        status = TaskStatus.RUNNING,
                        message = "Running...",
                        isRunning = true
                    )
                } else {
                    Logger.logWarn(LOG_TAG, "Cannot transition to RUNNING: already running different task: $currentTask")
                    StateTransitionResult.Error("Task is already running: $currentTask")
                }
            }
        }
    }
    
    /**
     * Transition to success state when task completes successfully
     */
    fun transitionToSuccess(): StateTransitionResult {
        return when (currentState) {
            AgentState.RUNNING -> {
                Logger.logInfo(LOG_TAG, "Transitioning to SUCCESS state for task: $currentTask")
                val task = currentTask
                currentState = AgentState.SUCCESS
                currentTask = null
                StateTransitionResult.Success(
                    status = TaskStatus.SUCCESS,
                    message = "Task completed successfully",
                    isRunning = false
                )
            }
            else -> {
                Logger.logWarn(LOG_TAG, "Cannot transition to SUCCESS: current state is $currentState")
                StateTransitionResult.Error("Invalid state transition: cannot go to SUCCESS from $currentState")
            }
        }
    }
    
    /**
     * Transition to error state when task fails
     */
    fun transitionToError(): StateTransitionResult {
        return when (currentState) {
            AgentState.RUNNING -> {
                Logger.logInfo(LOG_TAG, "Transitioning to ERROR state for task: $currentTask")
                val task = currentTask
                currentState = AgentState.ERROR
                currentTask = null
                StateTransitionResult.Success(
                    status = TaskStatus.ERROR,
                    message = "An error occurred",
                    isRunning = false
                )
            }
            else -> {
                Logger.logWarn(LOG_TAG, "Cannot transition to ERROR: current state is $currentState")
                StateTransitionResult.Error("Invalid state transition: cannot go to ERROR from $currentState")
            }
        }
    }
    
    /**
     * Transition to idle state (reset)
     */
    fun transitionToIdle(): StateTransitionResult {
        Logger.logInfo(LOG_TAG, "Transitioning to IDLE state")
        currentState = AgentState.IDLE
        currentTask = null
        taskStartTime = 0
        return StateTransitionResult.Success(
            status = TaskStatus.STOPPED,
            message = "Ready",
            isRunning = false
        )
    }
    
    /**
     * Force stop current task (used when user stops task)
     */
    fun forceStop(): StateTransitionResult {
        Logger.logInfo(LOG_TAG, "Force stopping task: $currentTask")
        currentState = AgentState.IDLE
        val task = currentTask
        currentTask = null
        taskStartTime = 0
        return StateTransitionResult.Success(
            status = TaskStatus.STOPPED,
            message = "Task stopped",
            isRunning = false
        )
    }
    
    /**
     * Get current state
     */
    fun getCurrentState(): AgentState = currentState
    
    /**
     * Get current task
     */
    fun getCurrentTask(): String? = currentTask
    
    /**
     * Check if a task is currently running
     */
    fun isTaskRunning(): Boolean = currentState == AgentState.RUNNING
    
    /**
     * Result of a state transition
     */
    sealed class StateTransitionResult {
        data class Success(
            val status: TaskStatus,
            val message: String,
            val isRunning: Boolean
        ) : StateTransitionResult()
        
        data class Error(val message: String) : StateTransitionResult()
    }
}

