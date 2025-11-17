package com.termux.app;

import androidx.lifecycle.ViewModel;

/**
 * ViewModel to manage DriverActivity state and demonstrate ViewModel functionality.
 * This ViewModel survives configuration changes and can be used to store
 * UI-related data that should survive activity recreation.
 */
public class DriverViewModel extends ViewModel {
    
    private static final String LOG_TAG = "DriverViewModel";
    
    // Track command execution state
    private boolean isBootstrapReady = false;
    private boolean isCommandExecuting = false;
    private String lastExecutedCommand = "";
    private String lastCommandOutput = "";
    private int commandExecutionCount = 0;
    
    // ViewModel lifecycle - called when ViewModel is no longer used
    @Override
    protected void onCleared() {
        super.onCleared();
        com.termux.shared.logger.Logger.logDebug(LOG_TAG, "ViewModel cleared");
    }
    
    // Getters and setters
    public boolean isBootstrapReady() {
        return isBootstrapReady;
    }
    
    public void setBootstrapReady(boolean bootstrapReady) {
        this.isBootstrapReady = bootstrapReady;
        com.termux.shared.logger.Logger.logDebug(LOG_TAG, "Bootstrap ready: " + bootstrapReady);
    }
    
    public boolean isCommandExecuting() {
        return isCommandExecuting;
    }
    
    public void setCommandExecuting(boolean commandExecuting) {
        this.isCommandExecuting = commandExecuting;
        com.termux.shared.logger.Logger.logDebug(LOG_TAG, "Command executing: " + commandExecuting);
    }
    
    public String getLastExecutedCommand() {
        return lastExecutedCommand;
    }
    
    public void setLastExecutedCommand(String command) {
        this.lastExecutedCommand = command;
        commandExecutionCount++;
        com.termux.shared.logger.Logger.logDebug(LOG_TAG, "Last command: " + command + " (count: " + commandExecutionCount + ")");
    }
    
    public String getLastCommandOutput() {
        return lastCommandOutput;
    }
    
    public void setLastCommandOutput(String output) {
        this.lastCommandOutput = output;
    }
    
    public int getCommandExecutionCount() {
        return commandExecutionCount;
    }
    
    /**
     * Get a status message indicating ViewModel is working
     */
    public String getViewModelStatus() {
        return "ViewModel Active - Commands executed: " + commandExecutionCount;
    }
}

