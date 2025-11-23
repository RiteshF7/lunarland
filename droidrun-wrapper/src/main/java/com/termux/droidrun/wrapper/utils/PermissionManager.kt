package com.termux.droidrun.wrapper.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.termux.droidrun.portal.DroidrunSDK
import com.termux.droidrun.portal.DroidrunKeyboardIME

/**
 * PermissionManager - Checks and requests required permissions
 * 
 * Manages:
 * - Accessibility Service permission
 * - Keyboard IME permission
 * - Other runtime permissions
 */
class PermissionManager(private val context: Context) {
    companion object {
        private const val TAG = "PermissionManager"
    }
    
    private val sdk = DroidrunSDK.getInstance()
    
    /**
     * Check if all required permissions are granted
     * Note: Keyboard IME only needs to be enabled, not necessarily selected as default
     */
    fun areAllPermissionsGranted(): Boolean {
        return isAccessibilityServiceEnabled() && (isKeyboardIMEEnabled() || isKeyboardIMESelected())
    }
    
    /**
     * Check if Accessibility Service is enabled
     * Note: We need to check using the wrapper app's package name, not the SDK's
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            // The SDK checks using its own package name, but the service is registered
            // in the wrapper app's manifest, so we need to check manually
            val serviceName = "${context.packageName}/com.termux.droidrun.portal.DroidrunAccessibilityService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val isEnabled = enabledServices.contains(serviceName)
            Log.d(TAG, "Accessibility service check: $serviceName in enabled services: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if Keyboard IME is enabled
     * This checks if the IME is enabled in system settings
     */
    fun isKeyboardIMEEnabled(): Boolean {
        return try {
            val packageName = context.packageName
            val className = DroidrunKeyboardIME::class.java.canonicalName
            val simpleClassName = DroidrunKeyboardIME::class.java.simpleName
            
            // Method 1: Check if IME instance is available (when keyboard is active)
            if (DroidrunKeyboardIME.isAvailable()) {
                Log.d(TAG, "Keyboard IME instance is available (keyboard is active)")
                return true
            }
            
            // Method 2: Check PackageManager to see if IME component is enabled
            val pm = context.packageManager
            val componentName = android.content.ComponentName(packageName, className)
            val componentState = pm.getComponentEnabledSetting(componentName)
            if (componentState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                componentState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                Log.d(TAG, "IME component is enabled in PackageManager")
                return true
            }
            
            // Method 3: Try to read ENABLED_INPUT_METHODS (works on Android < 14)
            try {
                val enabledIMEs = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_INPUT_METHODS
                ) ?: ""
                
                // Try different IME ID formats
                val imeId1 = "$packageName/$className"
                val imeId2 = "$packageName/.$simpleClassName"
                val imeId3 = "$packageName/$simpleClassName"
                
                Log.d(TAG, "Checking IME status. Package: $packageName, Class: $className")
                Log.d(TAG, "Enabled IMEs: $enabledIMEs")
                Log.d(TAG, "Looking for: $imeId1, $imeId2, $imeId3")
                
                // Check multiple formats
                val enabled = enabledIMEs.contains(imeId1) || 
                            enabledIMEs.contains(imeId2) ||
                            enabledIMEs.contains(imeId3) ||
                            enabledIMEs.contains(className) ||
                            enabledIMEs.contains(simpleClassName) ||
                            enabledIMEs.contains("DroidrunKeyboardIME") ||
                            enabledIMEs.contains("Droidrun Keyboard")
                
                if (enabled) {
                    Log.d(TAG, "IME is enabled in settings")
                    return true
                } else {
                    Log.w(TAG, "IME not found in enabled IMEs list")
                }
            } catch (e: SecurityException) {
                // On Android 14+ (targetSdk 34+), we can't read ENABLED_INPUT_METHODS
                // This is expected - we'll rely on PackageManager check above
                Log.d(TAG, "Cannot read ENABLED_INPUT_METHODS (Android 14+), using PackageManager check")
            }
            
            // Method 4: Check InputMethodManager for enabled IMEs (alternative approach)
            try {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val inputMethods = imm.enabledInputMethodList
                val imeId = "$packageName/$className"
                for (inputMethod in inputMethods) {
                    if (inputMethod.id == imeId || 
                        inputMethod.id.contains("DroidrunKeyboardIME") ||
                        inputMethod.packageName == packageName) {
                        Log.d(TAG, "IME found in InputMethodManager enabled list")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking InputMethodManager: ${e.message}")
            }
            
            Log.w(TAG, "IME not detected as enabled by any method")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard IME: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if Keyboard IME is currently selected
     */
    fun isKeyboardIMESelected(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val currentInputMethod = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            val keyboardServiceName = "${context.packageName}/${DroidrunKeyboardIME::class.java.canonicalName}"
            currentInputMethod == keyboardServiceName
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard IME selection: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get intent to open Accessibility Settings
     */
    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
    
    /**
     * Get intent to open Input Method Settings
     */
    fun getInputMethodSettingsIntent(): Intent {
        return Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
    }
    
    /**
     * Get permission status summary
     */
    fun getPermissionStatus(): PermissionStatus {
        return PermissionStatus(
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            keyboardIMEEnabled = isKeyboardIMEEnabled(),
            keyboardIMESelected = isKeyboardIMESelected(),
            allGranted = areAllPermissionsGranted()
        )
    }
}

/**
 * Permission status data class
 */
data class PermissionStatus(
    val accessibilityEnabled: Boolean,
    val keyboardIMEEnabled: Boolean,
    val keyboardIMESelected: Boolean,
    val allGranted: Boolean
)

