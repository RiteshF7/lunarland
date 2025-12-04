package com.termux.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.termux.shared.logger.Logger

/**
 * BroadcastReceiver to handle notification actions for TaskExecutor
 */
class TaskExecutorNotificationReceiver : BroadcastReceiver() {
    
    private val LOG_TAG = "TaskExecutorNotificationReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.termux.app.STOP_TASK" -> {
                Logger.logInfo(LOG_TAG, "Stop task action received")
                // Send broadcast to TaskExecutorActivity to stop the task
                val stopIntent = Intent("com.termux.app.ACTION_STOP_TASK").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(stopIntent)
            }
        }
    }
}

