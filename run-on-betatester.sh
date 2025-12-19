#!/bin/bash
# Script to build and run the app on betatester emulator

set -e

AVD_NAME="betatester"
PACKAGE_NAME="com.termux"
ACTIVITY_NAME="com.termux/.app.LunarHomeScreenActivity"

echo "========================================"
echo "Building and Running on Betatester AVD"
echo "========================================"
echo ""

# Check if emulator is already running
echo "Checking for running emulators..."
if adb devices | grep -q "device$"; then
    echo "Emulator is already running."
else
    echo "Starting emulator $AVD_NAME..."
    emulator -avd "$AVD_NAME" &
    EMULATOR_PID=$!
    
    echo "Waiting for emulator to be ready..."
    adb wait-for-device
    while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
        echo "Still booting..."
        sleep 2
    done
    echo "Emulator is ready!"
fi

# Get the device ID for betatester
echo ""
echo "Finding betatester device..."
# Try to find betatester specifically, otherwise use first device
DEVICE_ID=$(adb devices -l | grep -i "betatester" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE_ID" ]; then
    # If betatester not found by name, use first available device
    DEVICE_ID=$(adb devices | grep "device$" | head -n 1 | awk '{print $1}')
fi
if [ -z "$DEVICE_ID" ]; then
    echo "Error: No device found!"
    exit 1
fi
echo "Found device: $DEVICE_ID"

# Build and install the app
echo ""
echo "Building and installing the app..."
./gradlew installDebug
if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

# Launch the app
echo ""
echo "Launching the app..."
adb -s "$DEVICE_ID" shell am start -n "$ACTIVITY_NAME"
if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "App launched successfully!"
    echo "========================================"
else
    echo ""
    echo "Failed to launch app. Trying alternative method..."
    adb -s "$DEVICE_ID" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1
fi

