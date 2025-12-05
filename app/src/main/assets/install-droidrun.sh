#!/data/data/com.termux/files/usr/bin/bash
# install-droidrun.sh
# Installation script for droidrun dependencies using local wheels first
# This script prioritizes local wheels from ~/wheels directory

# Configuration
SCRIPT_START_TIME=$(date +%s)
LOG_FILE="$HOME/wheels/install-droidrun.log"
WHEELS_DIR="$HOME/wheels"
TOTAL_STEPS=12
CURRENT_STEP=0
SUCCESS_COUNT=0
FAILED_COUNT=0
SKIPPED_COUNT=0
FAILED_PACKAGES=()

# Pip command variable (will be set during setup)
PIP_CMD="pip"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Create wheels directory and log file
mkdir -p "$WHEELS_DIR"
touch "$LOG_FILE"

# Logging function
log() {
    local level="$1"
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local log_entry="[$timestamp] [$level] $message"
    
    echo "$log_entry" >> "$LOG_FILE"
    
    case "$level" in
        "INFO")
            echo -e "${BLUE}[INFO]${NC} $message"
            ;;
        "SUCCESS")
            echo -e "${GREEN}[SUCCESS]${NC} $message"
            ;;
        "ERROR")
            echo -e "${RED}[ERROR]${NC} $message"
            ;;
        "WARNING")
            echo -e "${YELLOW}[WARNING]${NC} $message"
            ;;
        "STEP")
            echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            echo -e "${BLUE}[STEP $CURRENT_STEP/$TOTAL_STEPS]${NC} $message"
            echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            ;;
        *)
            echo "$message"
            ;;
    esac
}

# Function to get elapsed time
get_elapsed_time() {
    local start_time=$1
    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))
    local minutes=$((elapsed / 60))
    local seconds=$((elapsed % 60))
    printf "%02d:%02d" "$minutes" "$seconds"
}

# Function to check if package is already installed
is_package_installed() {
    local package_name="$1"
    # Ensure PATH includes termux prefix
    export PATH="$PREFIX/bin:$PATH"
    $PIP_CMD show "$package_name" &>/dev/null
}

# Function to get installed version
get_installed_version() {
    local package_name="$1"
    # Ensure PATH includes termux prefix
    export PATH="$PREFIX/bin:$PATH"
    # Try pip show first
    local version=$($PIP_CMD show "$package_name" 2>/dev/null | grep "^Version:" | awk '{print $2}')
    if [ -n "$version" ]; then
        echo "$version"
        return 0
    fi
    # Fallback: try python import
    local import_name=$(echo "$package_name" | tr '-' '_')
    version=$(python3 -c "import $import_name; print($import_name.__version__)" 2>/dev/null || echo "")
    if [ -n "$version" ]; then
        echo "$version"
        return 0
    fi
    echo "not installed"
    return 1
}

# Function to find local wheel file
find_local_wheel() {
    local package_name="$1"
    # Try with hyphen first (e.g., scikit-learn-*.whl)
    local wheel_file=$(ls -t "$WHEELS_DIR"/${package_name}-*.whl 2>/dev/null | head -1)
    if [ -n "$wheel_file" ] && [ -f "$wheel_file" ]; then
        echo "$wheel_file"
        return 0
    fi
    # Try with underscore (e.g., scikit_learn-*.whl)
    local package_name_underscore=$(echo "$package_name" | tr '-' '_')
    wheel_file=$(ls -t "$WHEELS_DIR"/${package_name_underscore}-*.whl 2>/dev/null | head -1)
    if [ -n "$wheel_file" ] && [ -f "$wheel_file" ]; then
        echo "$wheel_file"
        return 0
    fi
    # Try case-insensitive search
    wheel_file=$(find "$WHEELS_DIR" -maxdepth 1 -iname "${package_name}-*.whl" -o -iname "${package_name_underscore}-*.whl" 2>/dev/null | head -1)
    if [ -n "$wheel_file" ] && [ -f "$wheel_file" ]; then
        echo "$wheel_file"
        return 0
    fi
    return 1
}

# Function to validate wheel file
validate_wheel_file() {
    local wheel_file="$1"
    
    if [ ! -f "$wheel_file" ]; then
        log "ERROR" "Wheel file does not exist: $wheel_file"
        return 1
    fi
    
    local file_size=$(stat -f%z "$wheel_file" 2>/dev/null || stat -c%s "$wheel_file" 2>/dev/null || echo "0")
    if [ "$file_size" -eq 0 ]; then
        log "ERROR" "Wheel file is empty: $wheel_file"
        return 1
    fi
    
    log "INFO" "Wheel file size: $file_size bytes"
    
    # Check if it's a valid ZIP file (wheels are ZIP files)
    if ! unzip -t "$wheel_file" >/dev/null 2>&1; then
        log "ERROR" "Wheel file is not a valid ZIP: $wheel_file"
        log "ERROR" "ZIP test output: $(unzip -t "$wheel_file" 2>&1 | head -3)"
        return 1
    fi
    
    log "INFO" "Wheel file is valid ZIP: $wheel_file"
    return 0
}

# Function to install from local wheel directly using pip install filename.whl
install_from_local_wheel() {
    local package_name="$1"
    local wheel_file=$(find_local_wheel "$package_name")
    
    if [ -n "$wheel_file" ] && [ -f "$wheel_file" ]; then
        log "INFO" "Found local wheel: $wheel_file"
        
        # Validate wheel file before installation
        if ! validate_wheel_file "$wheel_file"; then
            log "ERROR" "Wheel file validation failed for $wheel_file, skipping installation"
            return 1
        fi
        
        log "INFO" "Installing $package_name from local wheel using: pip install $wheel_file"
        
        # Ensure PATH includes termux prefix
        export PATH="$PREFIX/bin:$PATH"
        
        # Make sure wheel file is readable
        chmod 644 "$wheel_file" 2>/dev/null || true
        
        # Install directly using pip install filename.whl
        log "INFO" "Running: $PIP_CMD install \"$wheel_file\""
        local install_output=$($PIP_CMD install "$wheel_file" 2>&1 | tee -a "$LOG_FILE")
        local pip_exit_code=${PIPESTATUS[0]}
        
        # Log full pip output for debugging
        log "INFO" "Pip install output for $package_name:"
        echo "$install_output" | while IFS= read -r line; do
            log "INFO" "  $line"
        done
        
        if [ $pip_exit_code -eq 0 ]; then
            # Wait a moment for installation to complete
            sleep 1
            local installed_version=$(get_installed_version "$package_name")
            if [ "$installed_version" != "not installed" ] && [ -n "$installed_version" ]; then
                log "SUCCESS" "$package_name installed from local wheel (Version: $installed_version)"
                return 0
            else
                # Try to check if it's installed by trying to import it
                local import_name=$(echo "$package_name" | tr '-' '_')
                if python3 -c "import $import_name" 2>/dev/null; then
                    log "SUCCESS" "$package_name installed from local wheel (verified by import)"
                    return 0
                fi
                log "ERROR" "pip reported success but package not found after installation"
                log "ERROR" "Package name: $package_name"
                log "ERROR" "Import name: $import_name"
                log "ERROR" "Pip show output: $($PIP_CMD show "$package_name" 2>&1 | head -10)"
                log "ERROR" "Install output: $(echo "$install_output" | grep -E '(ERROR|WARNING|Processing|Installing)' | tail -10)"
                return 1
            fi
        else
            log "ERROR" "Failed to install from local wheel (pip exit code: $pip_exit_code)"
            log "ERROR" "Package name: $package_name"
            log "ERROR" "Wheel file: $wheel_file"
            log "ERROR" "File size: $(stat -f%z "$wheel_file" 2>/dev/null || stat -c%s "$wheel_file" 2>/dev/null || echo 'unknown') bytes"
            log "ERROR" "Install output: $(echo "$install_output" | grep -E '(ERROR|WARNING|Processing|Installing)' | tail -20)"
            return 1
        fi
    fi
    return 1
}

# Function to install a package with wheel building
install_package_with_wheel() {
    local package_name="$1"
    local version_constraint="${2:-}"
    local package_spec="$package_name"
    local step_start_time=$(date +%s)
    local build_required=true
    local wheel_created=false
    
    CURRENT_STEP=$((CURRENT_STEP + 1))
    
    if [ -n "$version_constraint" ]; then
        package_spec="$package_name$version_constraint"
    fi
    
    log "STEP" "$package_name - Starting installation"
    log "INFO" "Package spec: $package_spec"
    
    # Check if already installed with correct version
    if is_package_installed "$package_name"; then
        local installed_version=$(get_installed_version "$package_name")
        log "INFO" "Package $package_name is already installed (version: $installed_version)"
        
        # For packages with constraints, check if version matches using Python packaging
        if [ -n "$version_constraint" ]; then
            local min_version=$(echo "$version_constraint" | grep -oP '>=\K[0-9.]+' | head -1)
            local max_version=$(echo "$version_constraint" | grep -oP '<\K[0-9.]+' | head -1)
            
            if [ -n "$min_version" ] || [ -n "$max_version" ]; then
                local version_check=$(python3 -c "
import sys
from packaging import version
installed = '$installed_version'
min_v = '$min_version' if '$min_version' else '0'
max_v = '$max_version' if '$max_version' else '999'
try:
    v = version.parse(installed)
    min_ok = version.parse(min_v) <= v if min_v != '0' else True
    max_ok = v < version.parse(max_v) if max_v != '999' else True
    if min_ok and max_ok:
        print('OK')
    else:
        print('FAIL')
except:
    print('FAIL')
" 2>/dev/null || echo "FAIL")
                
                if [ "$version_check" = "OK" ]; then
                    log "INFO" "Installed version $installed_version satisfies constraint $version_constraint - skipping"
                    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
                    return 0
                fi
            fi
        else
            log "INFO" "Skipping $package_name - already installed"
            SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
            return 0
        fi
    fi
    
    # PRIORITY 1: Try to install from local wheel first
    if install_from_local_wheel "$package_name"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        return 0
    fi
    
    cd "$WHEELS_DIR" || exit 1
    
    # Ensure PATH includes termux prefix
    export PATH="$PREFIX/bin:$PATH"
    
    # Step 1: Download source distribution
    log "INFO" "No local wheel found. Downloading $package_spec..."
    # Ensure PATH includes termux prefix (redundant but safe)
    export PATH="$PREFIX/bin:$PATH"
    if $PIP_CMD download "$package_spec" --dest . --no-cache-dir 2>&1 | tee -a "$LOG_FILE"; then
        log "INFO" "Download completed"
    else
        log "ERROR" "Failed to download $package_spec"
        FAILED_COUNT=$((FAILED_COUNT + 1))
        FAILED_PACKAGES+=("$package_name (download failed)")
        return 1
    fi
    
    # Step 2: Build wheel from source
    local source_file=""
    source_file=$(ls -t ${package_name}-*.tar.gz 2>/dev/null | head -1)
    
    if [ -z "$source_file" ] || [ ! -f "$source_file" ]; then
        log "WARNING" "No source file found for $package_name, checking for pre-built wheel..."
        local existing_wheel=$(ls ${package_name}-*.whl 2>/dev/null | head -1)
        if [ -n "$existing_wheel" ]; then
            log "INFO" "Using existing wheel: $existing_wheel"
            build_required=false
        else
            log "ERROR" "No source file or wheel found for $package_name"
            FAILED_COUNT=$((FAILED_COUNT + 1))
            FAILED_PACKAGES+=("$package_name (no source/wheel)")
            return 1
        fi
    fi
    
    if [ "$build_required" = true ]; then
        log "INFO" "Building wheel from $source_file (this may take a while)..."
        local build_start=$(date +%s)
        
        # Ensure PATH includes termux prefix
        export PATH="$PREFIX/bin:$PATH"
        
        if $PIP_CMD wheel --no-deps --wheel-dir . "$source_file" 2>&1 | tee -a "$LOG_FILE"; then
            local build_time=$(get_elapsed_time "$build_start")
            log "INFO" "Wheel built successfully (time: $build_time)"
            wheel_created=true
        else
            log "ERROR" "Failed to build wheel for $package_name"
            FAILED_COUNT=$((FAILED_COUNT + 1))
            FAILED_PACKAGES+=("$package_name (build failed)")
            return 1
        fi
    fi
    
    # Step 3: Install from wheel
    local wheel_file=$(ls -t ${package_name}-*.whl 2>/dev/null | head -1)
    if [ -z "$wheel_file" ] || [ ! -f "$wheel_file" ]; then
        log "ERROR" "No wheel file found for $package_name after build"
        FAILED_COUNT=$((FAILED_COUNT + 1))
        FAILED_PACKAGES+=("$package_name (no wheel file)")
        return 1
    fi
    
    log "INFO" "Installing from wheel: $wheel_file"
    
    # Uninstall old version if exists (for packages that might have version conflicts)
    if [[ "$package_name" == "pandas" ]]; then
        log "INFO" "Uninstalling any existing pandas version..."
        # Ensure PATH includes termux prefix
        export PATH="$PREFIX/bin:$PATH"
        $PIP_CMD uninstall -y pandas 2>/dev/null || true
    fi
    
    # Ensure PATH includes termux prefix
    export PATH="$PREFIX/bin:$PATH"
    
    if $PIP_CMD install --find-links . --no-index "$wheel_file" 2>&1 | tee -a "$LOG_FILE"; then
        local installed_version=$(get_installed_version "$package_name")
        local total_time=$(get_elapsed_time "$step_start_time")
        log "SUCCESS" "$package_name installed successfully (Version: $installed_version, Time: $total_time)"
        
        if [ "$wheel_created" = true ]; then
            log "INFO" "Wheel file preserved: $wheel_file"
        fi
        
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        return 0
    else
        log "ERROR" "Failed to install $package_name from wheel"
        FAILED_COUNT=$((FAILED_COUNT + 1))
        FAILED_PACKAGES+=("$package_name (installation failed)")
        return 1
    fi
}

# Function to install pure Python package (no build needed)
install_pure_python_package() {
    local package_name="$1"
    local version_constraint="${2:-}"
    local package_spec="$package_name"
    local step_start_time=$(date +%s)
    
    CURRENT_STEP=$((CURRENT_STEP + 1))
    
    if [ -n "$version_constraint" ]; then
        package_spec="$package_name$version_constraint"
    fi
    
    log "STEP" "$package_name - Installing pure Python package"
    
    if is_package_installed "$package_name"; then
        local installed_version=$(get_installed_version "$package_name")
        log "INFO" "Package $package_name is already installed (version: $installed_version)"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        return 0
    fi
    
    # PRIORITY 1: Try to install from local wheel first
    if install_from_local_wheel "$package_name"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        return 0
    fi
    
    # Ensure PATH includes termux prefix
    export PATH="$PREFIX/bin:$PATH"
    
    log "INFO" "No local wheel found. Installing $package_spec..."
    # Use --find-links to prioritize local wheels even for transitive dependencies
    if $PIP_CMD install "$package_spec" --find-links "$WHEELS_DIR" --prefer-binary 2>&1 | tee -a "$LOG_FILE"; then
        local installed_version=$(get_installed_version "$package_name")
        local total_time=$(get_elapsed_time "$step_start_time")
        log "SUCCESS" "$package_name installed successfully (Version: $installed_version, Time: $total_time)"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        return 0
    else
        log "ERROR" "Failed to install $package_name"
        FAILED_COUNT=$((FAILED_COUNT + 1))
        FAILED_PACKAGES+=("$package_name (installation failed)")
        return 1
    fi
}

# Main installation function
main() {
    local script_start=$(date '+%Y-%m-%d %H:%M:%S')
    
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  Installing droidrun[google] Dependencies${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    log "INFO" "Installation started at $script_start"
    log "INFO" "Wheels directory: $WHEELS_DIR"
    log "INFO" "Log file: $LOG_FILE"
    echo ""
    
    # Phase 1: System Setup
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Phase 1: System Setup${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    log "INFO" "Setting up environment..."
    
    # Ensure PATH includes termux prefix
    export PATH="$PREFIX/bin:$PATH"
    log "INFO" "Updated PATH: $PATH"
    
    # Update and upgrade packages
    log "INFO" "Updating package lists..."
    pkg update -y 2>&1 | tee -a "$LOG_FILE" || {
        log "WARNING" "pkg update failed, continuing..."
    }
    
    log "INFO" "Upgrading packages..."
    pkg upgrade -y 2>&1 | tee -a "$LOG_FILE" || {
        log "WARNING" "pkg upgrade failed, continuing..."
    }
    
    # Install/ensure pip is available
    log "INFO" "Ensuring pip is installed..."
    
    # Check for pip or pip3
    if command -v pip &> /dev/null; then
        PIP_CMD="pip"
        log "INFO" "Found pip command"
    elif command -v pip3 &> /dev/null; then
        PIP_CMD="pip3"
        log "INFO" "Found pip3 command, using it as pip"
    else
        log "INFO" "pip not found, installing python-pip..."
        pkg install -y python-pip 2>&1 | tee -a "$LOG_FILE" || {
            log "WARNING" "python-pip installation failed, trying python..."
            pkg install -y python 2>&1 | tee -a "$LOG_FILE" || {
                log "ERROR" "Failed to install python"
                exit 1
            }
        }
        
        # Check again after installation
        if command -v pip &> /dev/null; then
            PIP_CMD="pip"
        elif command -v pip3 &> /dev/null; then
            PIP_CMD="pip3"
        elif python3 -m pip --version &> /dev/null; then
            PIP_CMD="python3 -m pip"
            log "INFO" "Using python3 -m pip"
        else
            log "ERROR" "pip command still not found after installation"
            log "INFO" "Trying to use python3 -m pip as fallback..."
            PIP_CMD="python3 -m pip"
        fi
    fi
    
    # Verify pip is accessible
    if ! $PIP_CMD --version &> /dev/null; then
        log "ERROR" "pip command still not working: $PIP_CMD"
        exit 1
    fi
    
    log "INFO" "Using pip command: $PIP_CMD"
    log "INFO" "pip version: $($PIP_CMD --version 2>&1 || echo 'unknown')"
    
    # Set parallelism limits
    export NINJAFLAGS="-j2"
    export MAKEFLAGS="-j2"
    export MAX_JOBS=2
    log "INFO" "Set parallelism limits: NINJAFLAGS=$NINJAFLAGS, MAKEFLAGS=$MAKEFLAGS"
    
    # Check for gfortran symlink
    if [ ! -f "$PREFIX/bin/gfortran" ]; then
        if [ -f "$PREFIX/bin/flang" ]; then
            ln -sf "$PREFIX/bin/flang" "$PREFIX/bin/gfortran"
            log "INFO" "Created gfortran symlink to flang"
        else
            log "WARNING" "flang not found, gfortran symlink not created"
        fi
    else
        log "INFO" "gfortran symlink already exists"
    fi
    
    # Install necessary system packages for droidrun
    log "INFO" "Installing necessary system packages for droidrun..."
    pkg install -y \
        binutils \
        clang \
        cmake \
        git \
        libc++ \
        make \
        pkg-config \
        rust \
        2>&1 | tee -a "$LOG_FILE" || {
        log "WARNING" "Some system packages failed to install, continuing..."
    }
    
    # Upgrade pip and build tools
    log "INFO" "Upgrading pip and build tools..."
    $PIP_CMD install --upgrade pip wheel build setuptools 2>&1 | tee -a "$LOG_FILE" || {
        log "WARNING" "Failed to upgrade some build tools, continuing..."
    }
    
    echo ""
    
    # Phase 2: Install all wheels in correct dependency order
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Phase 2: Installing Local Wheels${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    cd "$WHEELS_DIR" || exit 1
    export PATH="$PREFIX/bin:$PATH"
    
    # Make sure wheels directory and files are readable/executable
    log "INFO" "Setting permissions on wheels directory..."
    chmod -R u+rwX "$WHEELS_DIR" 2>/dev/null || true
    find "$WHEELS_DIR" -name "*.whl" -type f -exec chmod 644 {} \; 2>/dev/null || true
    
    # Define installation order (dependencies first)
    local wheel_order=(
        "numpy"
        "patchelf"
        "Cython"
        "meson-python"
        "scipy"
        "pandas"
        "scikit-learn"
        "maturin"
        "jiter"
        "pyarrow"
        "psutil"
        "cffi"
        "cryptography"
        "greenlet"
        "grpcio"
        "markupsafe"
        "orjson"
        "pillow"
        "pydantic_core"
        "pyyaml"
        "regex"
        "sqlean_py"
        "tiktoken"
        "typing_extensions"
        "aiohttp"
        "apkutils2"
    )
    
    log "INFO" "Installing wheels in dependency order..."
    
    for package_name in "${wheel_order[@]}"; do
        # Skip if already installed
        if is_package_installed "$package_name"; then
            local installed_version=$(get_installed_version "$package_name")
            log "INFO" "Package $package_name is already installed (version: $installed_version) - skipping"
            SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
            continue
        fi
        
        # Try to find and install wheel
        if install_from_local_wheel "$package_name"; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            CURRENT_STEP=$((CURRENT_STEP + 1))
        else
            log "WARNING" "Wheel not found or failed to install for $package_name, will try PyPI later if needed"
        fi
    done
    
    # Install any remaining wheels not in the ordered list
    log "INFO" "Installing any remaining wheels..."
    for wheel_file in "$WHEELS_DIR"/*.whl; do
        if [ -f "$wheel_file" ]; then
            local wheel_name=$(basename "$wheel_file" .whl | sed 's/-.*//')
            # Check if already processed
            local already_processed=false
            for pkg in "${wheel_order[@]}"; do
                if [ "$wheel_name" = "$pkg" ] || [ "$wheel_name" = "$(echo $pkg | tr '-' '_')" ]; then
                    already_processed=true
                    break
                fi
            done
            
            if [ "$already_processed" = false ] && ! is_package_installed "$wheel_name"; then
                log "INFO" "Installing remaining wheel: $(basename "$wheel_file")"
                # Use --find-links to ensure transitive dependencies also use local wheels
                if $PIP_CMD install "$wheel_file" --find-links "$WHEELS_DIR" --prefer-binary 2>&1 | tee -a "$LOG_FILE"; then
                    SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
                else
                    log "WARNING" "Failed to install $(basename "$wheel_file")"
                fi
            fi
        fi
    done
    
    cd "$HOME" || exit 1
    echo ""
    
    # Phase 3: Install droidrun[google] (old Phase 3-5 removed, now just install droidrun)
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Phase 3: Installing droidrun[google]${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    CURRENT_STEP=$((CURRENT_STEP + 1))
    log "STEP" "droidrun[google] - Installing main package"
    
    # Ensure PATH includes termux prefix
    export PATH="$PREFIX/bin:$PATH"
    
    log "INFO" "Installing droidrun[google]..."
    
    # Install droidrun[google] - prioritize local wheels for ALL dependencies (including transitive)
    # Use --find-links to check local wheels first, but allow PyPI fallback if not found
    # This ensures transitive dependencies also use local wheels when available
    log "INFO" "Installing droidrun[google] with local wheel priority for all dependencies..."
    local install_output=$($PIP_CMD install 'droidrun[google]' --find-links "$WHEELS_DIR" --prefer-binary 2>&1 | tee -a "$LOG_FILE")
    local pip_exit_code=${PIPESTATUS[0]}
    
    if [ $pip_exit_code -eq 0 ]; then
        # Verify installation by checking if droidrun is actually installed
        local installed_version=$(get_installed_version "droidrun")
        
        if [ "$installed_version" != "not installed" ] && [ -n "$installed_version" ]; then
            log "SUCCESS" "droidrun[google] installed successfully (Version: $installed_version)"
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            
            # Additional verification: try importing droidrun
            log "INFO" "Verifying droidrun installation..."
            if python3 -c "import droidrun" 2>/dev/null; then
                log "SUCCESS" "droidrun import test passed"
            else
                log "WARNING" "droidrun import test failed, but package is installed"
            fi
        else
            log "ERROR" "droidrun[google] installation reported success but package not found"
            log "ERROR" "Attempted to verify with: pip show droidrun"
            FAILED_COUNT=$((FAILED_COUNT + 1))
            FAILED_PACKAGES+=("droidrun[google] (verification failed)")
            return 1
        fi
    else
        log "ERROR" "Failed to install droidrun[google] (pip exit code: $pip_exit_code)"
        FAILED_COUNT=$((FAILED_COUNT + 1))
        FAILED_PACKAGES+=("droidrun[google] (installation failed)")
        return 1
    fi
    
    echo ""
    
    # Summary Report
    local script_end_time=$(date +%s)
    local total_time=$(get_elapsed_time "$SCRIPT_START_TIME")
    local wheel_count=$(ls -1 "$WHEELS_DIR"/*.whl 2>/dev/null | wc -l)
    
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  Installation Summary${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    log "INFO" "Total packages processed: $TOTAL_STEPS"
    log "INFO" "Successfully installed: $SUCCESS_COUNT"
    log "INFO" "Failed: $FAILED_COUNT"
    log "INFO" "Skipped: $SKIPPED_COUNT"
    log "INFO" "Total time: $total_time"
    log "INFO" "Wheel files in directory: $wheel_count"
    log "INFO" "Wheels location: $WHEELS_DIR"
    
    if [ ${#FAILED_PACKAGES[@]} -gt 0 ]; then
        echo ""
        echo -e "${RED}Failed Packages:${NC}"
        for pkg in "${FAILED_PACKAGES[@]}"; do
            echo -e "  ${RED}✗${NC} $pkg"
        done
    fi
    
    echo ""
    echo -e "${BLUE}Wheel Files Created:${NC}"
    ls -lh "$WHEELS_DIR"/*.whl 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}' || echo "  No wheel files found"
    
    echo ""
    if [ $FAILED_COUNT -eq 0 ]; then
        echo -e "${GREEN}✓ All dependencies installed successfully!${NC}"
        log "INFO" "Installation completed successfully at $(date '+%Y-%m-%d %H:%M:%S')"
    else
        echo -e "${YELLOW}⚠ Installation completed with $FAILED_COUNT failure(s)${NC}"
        log "WARNING" "Installation completed with failures at $(date '+%Y-%m-%d %H:%M:%S')"
        echo ""
        echo -e "${YELLOW}Review the log file for details: $LOG_FILE${NC}"
    fi
    
    echo ""
    echo -e "${BLUE}Next Steps:${NC}"
    echo "  1. Verify installation: $PIP_CMD list | grep -E 'pandas|numpy|scipy|scikit-learn|jiter|droidrun'"
    echo "  2. Test imports: python -c 'import pandas, numpy, scipy, sklearn, jiter, droidrun'"
    echo "  3. Check log file: $LOG_FILE"
    echo ""
}

# Run main function
main "$@"

