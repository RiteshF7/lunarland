# Droidrun Integration Testing Guide

## Overview
This guide explains how to test the droidrun integration in termux-app.

## Test Activity
A test activity `DroidrunTestActivity` has been created to verify the integration. It appears as "Droidrun Test" in the launcher.

## Building and Installing

### 1. Build the APK
```bash
cd termux-app
./gradlew :app:assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Install on Emulator/Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or use Android Studio to install and run.

## Testing Steps

### Step 1: Launch Test Activity
1. Open the Termux app on your device/emulator
2. You should see "Droidrun Test" in the launcher (or use the existing DriverActivity)
3. Tap on "Droidrun Test" to open the test activity

### Step 2: Initialize SDK & Python
1. Tap the "1. Initialize SDK & Python" button
2. The activity will:
   - Check permissions (Accessibility Service, Keyboard IME)
   - Initialize Python environment
   - Extract and install droidrun wheel from assets
3. Check the logs for:
   - ✓ Python environment initialized successfully
   - ✓ droidrun package installed successfully

### Step 3: Test Wheel Installation
1. Tap the "2. Test Wheel Installation" button (enabled after step 1)
2. The activity will:
   - Check Python version
   - Verify droidrun package is installed
   - Test importing droidrun module
3. Expected output:
   - ✓ Python found: Python 3.x.x
   - ✓ droidrun package is installed
   - ✓ droidrun import successful

### Step 4: Test Basic Functionality
1. Tap the "3. Test Basic Functionality" button
2. The activity will:
   - Test SDK methods (isAccessibilityServiceEnabled, isKeyboardIMEEnabled)
   - Test getFormattedState() if accessibility is enabled
   - Test StateBridge file paths
3. Expected output:
   - Status of services
   - Number of elements found (if accessibility enabled)
   - File paths for state communication

## What to Check

### ✅ Success Indicators:
- Python environment initializes without errors
- droidrun wheel installs from assets successfully
- droidrun module can be imported
- SDK methods work correctly
- StateBridge file paths are accessible

### ⚠️ Common Issues:

1. **Python not found**
   - Ensure Termux is installed and Python is available
   - Run `pkg install python` in Termux terminal

2. **Wheel installation fails**
   - Check that `droidrun-0.4.11-py3-none-any.whl` exists in assets
   - Check logs for extraction errors

3. **droidrun import fails**
   - Check if dependencies are installed (pip will install them automatically)
   - Check Python version (requires 3.11+)

4. **Accessibility Service not enabled**
   - Go to Settings > Accessibility
   - Enable "Droidrun Accessibility Service"
   - Restart the app

5. **Keyboard IME not enabled**
   - Go to Settings > System > Languages & input > Virtual keyboard
   - Enable "Droidrun Keyboard"
   - Select it as input method when needed

## Files Location

- **Test Activity**: `termux-app/app/src/main/java/com/termux/app/DroidrunTestActivity.kt`
- **Wheel File**: `termux-app/app/src/main/assets/droidrun-0.4.11-py3-none-any.whl`
- **Wrapper Script**: `termux-app/app/src/main/assets/droidrun_wrapper.py`
- **Python Source**: `termux-app/droidrun-python/`

## Next Steps

After successful testing:
1. The integration is verified and working
2. You can now use droidrun functionality in your app
3. Create custom Activities/UI as needed
4. Integrate droidrun into your workflow

## Troubleshooting

If tests fail:
1. Check the log output in the test activity
2. Use `adb logcat` to see detailed Android logs
3. Check Termux terminal for Python errors
4. Verify assets are included in APK (check APK contents)

