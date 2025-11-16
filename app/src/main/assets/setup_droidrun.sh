#!/bin/bash

# DroidRun Setup Script
# This script clones the DroidRun repository, installs dependencies, and sets up the Portal app

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DROIDRUN_REPO="https://github.com/droidrun/droidrun.git"
ADBUTILS_REPO="https://github.com/openatx/adbutils.git"
INSTALL_DIR="${1:-./droidrun}"

# Functions
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

check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is not installed. Please install it first."
        exit 1
    fi
}

# Check prerequisites
print_info "Checking prerequisites..."
check_command git
check_command python3
check_command pip3

# Check Python version
PYTHON_VERSION=$(python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
REQUIRED_VERSION="3.11"
if [ "$(printf '%s\n' "$REQUIRED_VERSION" "$PYTHON_VERSION" | sort -V | head -n1)" != "$REQUIRED_VERSION" ]; then
    print_error "Python 3.11+ is required. Found: $PYTHON_VERSION"
    exit 1
fi

print_success "Prerequisites check passed"

# Create installation directory
if [ -d "$INSTALL_DIR" ]; then
    print_warning "Directory $INSTALL_DIR already exists. Removing it..."
    rm -rf "$INSTALL_DIR"
fi

mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

# Clone DroidRun repository
print_info "Cloning DroidRun repository from GitHub..."
if git clone "$DROIDRUN_REPO" .; then
    print_success "DroidRun repository cloned successfully"
else
    print_error "Failed to clone DroidRun repository"
    exit 1
fi

# Initialize and update submodules
print_info "Initializing and updating git submodules..."
if git submodule update --init --recursive; then
    print_success "Submodules initialized and updated"
else
    print_warning "No submodules found or submodule update failed (this is okay)"
fi

# Optionally clone adbutils separately if not present as submodule
if [ ! -d "adbutils/.git" ]; then
    print_info "adbutils not found as submodule. Cloning separately..."
    if [ -d "adbutils" ]; then
        print_warning "adbutils directory exists but is not a git repo. Removing..."
        rm -rf adbutils
    fi
    if git clone "$ADBUTILS_REPO" adbutils; then
        print_success "adbutils cloned successfully"
    else
        print_warning "Failed to clone adbutils separately (may already be included in repo)"
    fi
else
    print_success "adbutils found as submodule"
fi

# Upgrade pip
# Note: Skipping pip upgrade in Termux as it's forbidden and would break python-pip package
# print_info "Upgrading pip..."
# pip3 install --upgrade pip --quiet --user

# Install/upgrade setuptools (required for building some dependencies)
print_info "Ensuring setuptools is up to date..."
if pip3 install --upgrade setuptools wheel --user --quiet; then
    print_success "setuptools and wheel updated"
else
    print_warning "Failed to update setuptools, continuing anyway..."
fi

# Install DroidRun with Google Gemini support in editable mode
print_info "Installing DroidRun with Google Gemini support..."
if pip3 install -e ".[google]" --user; then
    print_success "DroidRun installed successfully"
else
    print_error "Failed to install DroidRun"
    exit 1
fi

# Check if ADB is available
print_info "Checking for ADB..."
if command -v adb &> /dev/null; then
    print_success "ADB found"
    adb devices
else
    print_warning "ADB not found in PATH. Make sure Android SDK platform-tools are installed."
    print_warning "You may need to install ADB separately or add it to your PATH."
fi

# Check for connected devices
print_info "Checking for connected Android devices..."
DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    print_warning "No Android devices detected. Please connect a device or start an emulator."
    print_warning "You can run 'droidrun setup' later when a device is connected."
else
    print_success "Found $DEVICE_COUNT connected device(s)"
    
    # Run droidrun setup
    print_info "Running droidrun setup to install Portal app..."
    if droidrun setup; then
        print_success "DroidRun setup completed successfully!"
    else
        print_error "DroidRun setup failed. You can try running 'droidrun setup' manually later."
    fi
fi

# Summary
echo ""
print_success "=========================================="
print_success "DroidRun Setup Complete!"
print_success "=========================================="
echo ""
print_info "Installation directory: $(pwd)"
echo ""
print_info "To use DroidRun:"
echo "  1. Set your Google API key:"
echo "     export GOOGLE_API_KEY='your-api-key-here'"
echo ""
echo "  2. Make sure a device is connected:"
echo "     adb devices"
echo ""
echo "  3. Run your first command:"
echo "     droidrun 'Open Settings'"
echo ""
print_warning "Note: If setup didn't run automatically, connect a device and run:"
echo "  droidrun setup"
echo ""

