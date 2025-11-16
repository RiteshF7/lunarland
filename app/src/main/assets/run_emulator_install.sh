#!/bin/bash

# Emulator Install Script for Termux
# This script checks for ADB, connected devices, and attempts installation

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if ADB is available
print_info "Checking for ADB..."
if ! command -v adb &> /dev/null; then
    print_error "ADB not found in PATH"
    print_info "Attempting to use system ADB..."
    # Try to find ADB in common locations
    if [ -f "/system/bin/adb" ]; then
        export PATH="/system/bin:$PATH"
    elif [ -f "/data/local/tmp/adb" ]; then
        export PATH="/data/local/tmp:$PATH"
    else
        print_error "ADB not found. Please install Android SDK platform-tools."
        print_info "You can install it with: pkg install android-tools"
        exit 1
    fi
fi

if command -v adb &> /dev/null; then
    ADB_VERSION=$(adb version | head -n1)
    print_success "ADB found: $ADB_VERSION"
else
    print_error "ADB still not available after path adjustments"
    exit 1
fi

# Check for connected devices
print_info "Checking for connected Android devices..."
DEVICES=$(adb devices | grep -E "device$|emulator-" | wc -l || echo "0")

if [ "$DEVICES" -eq 0 ]; then
    print_warning "No Android devices detected"
    print_info "Listing all ADB devices:"
    adb devices
    print_info ""
    print_info "To connect a device:"
    print_info "  1. Enable USB debugging on your Android device"
    print_info "  2. Connect via USB or use 'adb connect <ip>:5555' for network"
    print_info "  3. Or start an emulator from your host machine"
    exit 1
else
    print_success "Found $DEVICES connected device(s)"
    echo ""
    print_info "Connected devices:"
    adb devices
    echo ""
fi

# Get device info
print_info "Getting device information..."
DEVICE_SERIAL=$(adb devices | grep -E "device$|emulator-" | head -n1 | cut -f1)
if [ -n "$DEVICE_SERIAL" ]; then
    print_info "Primary device: $DEVICE_SERIAL"
    DEVICE_MODEL=$(adb -s "$DEVICE_SERIAL" shell getprop ro.product.model 2>/dev/null || echo "Unknown")
    DEVICE_ANDROID=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.release 2>/dev/null || echo "Unknown")
    print_info "  Model: $DEVICE_MODEL"
    print_info "  Android: $DEVICE_ANDROID"
    echo ""
fi

# Check boot status
print_info "Checking device boot status..."
BOOT_COMPLETED=$(adb -s "$DEVICE_SERIAL" shell getprop sys.boot_completed 2>/dev/null || echo "0")
if [ "$BOOT_COMPLETED" != "1" ]; then
    print_warning "Device may not be fully booted (boot_completed=$BOOT_COMPLETED)"
    print_info "Waiting for device to be ready..."
    adb -s "$DEVICE_SERIAL" wait-for-device
    # Wait a bit more for boot to complete
    sleep 2
else
    print_success "Device is fully booted"
fi

# Check if we can get UI state (requires uiautomator or similar)
print_info "Checking UI automation capabilities..."
if adb -s "$DEVICE_SERIAL" shell "command -v uiautomator" &> /dev/null; then
    print_success "uiautomator is available"
    print_info "Getting current UI state..."
    adb -s "$DEVICE_SERIAL" shell uiautomator dump /dev/tty 2>/dev/null | head -n5 || print_warning "Could not dump UI state"
else
    print_warning "uiautomator not available on device"
    print_info "Trying alternative method..."
    # Try to get window info
    adb -s "$DEVICE_SERIAL" shell dumpsys window windows | grep -E "mCurrentFocus|mFocusedApp" | head -n2 || true
fi

echo ""
print_info "Installation check complete!"
print_info ""
print_info "To install the app, you need to:"
print_info "  1. Build the APK on your host machine: ./gradlew app:assembleDebug"
print_info "  2. Install it via ADB: adb install app/build/outputs/apk/debug/app-debug.apk"
print_info ""
print_info "Or from this device, if you have the APK:"
print_info "  adb install /path/to/app-debug.apk"

