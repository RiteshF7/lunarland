package com.termux.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Build

object FloatingVolumeManager {
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun startFloatingVolume(context: Context) {
        if (!isOverlayPermissionGranted(context)) {
            requestOverlayPermission(context)
            return
        }

        val intent = Intent(context, FloatingVolumeService::class.java).apply {
            action = FloatingVolumeService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopFloatingVolume(context: Context) {
        val intent = Intent(context, FloatingVolumeService::class.java).apply {
            action = FloatingVolumeService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun isServiceRunning(context: Context): Boolean {
        // Simple check - in production you might want to track this more accurately
        return false // This would need proper service state tracking
    }
}

