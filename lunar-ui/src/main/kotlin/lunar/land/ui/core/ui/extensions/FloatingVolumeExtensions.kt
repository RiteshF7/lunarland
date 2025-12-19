package lunar.land.ui.core.ui.extensions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Helper functions for floating volume control.
 * These functions work without requiring direct access to the app module.
 */

fun Context.isOverlayPermissionGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(this)
    } else {
        true
    }
}

fun Context.requestOverlayPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}

fun Context.startFloatingVolumeService() {
    if (!isOverlayPermissionGranted()) {
        requestOverlayPermission()
        return
    }

    val intent = Intent().apply {
        setClassName(packageName, "com.termux.app.FloatingVolumeService")
        action = "com.termux.app.START_FLOATING_VOLUME"
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

fun Context.stopFloatingVolumeService() {
    val intent = Intent().apply {
        setClassName(packageName, "com.termux.app.FloatingVolumeService")
        action = "com.termux.app.STOP_FLOATING_VOLUME"
    }
    startService(intent)
}

