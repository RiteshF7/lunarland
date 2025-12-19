@echo off
REM Script to build and run the app on betatester emulator
setlocal enabledelayedexpansion

set AVD_NAME=betatester
set PACKAGE_NAME=com.termux
set ACTIVITY_NAME=com.termux/.app.LunarHomeScreenActivity

echo ========================================
echo Building and Running on Betatester AVD
echo ========================================
echo.

REM Check if emulator is already running
echo Checking for running emulators...
adb devices | findstr /C:"device" >nul
if %errorlevel% equ 0 (
    echo Emulator is already running.
) else (
    echo Starting emulator %AVD_NAME%...
    start /B emulator -avd %AVD_NAME%
    
    echo Waiting for emulator to be ready...
    :wait_loop
    timeout /t 2 /nobreak >nul
    adb wait-for-device >nul 2>&1
    adb shell getprop sys.boot_completed | findstr /C:"1" >nul
    if %errorlevel% neq 0 (
        echo Still booting...
        goto wait_loop
    )
    echo Emulator is ready!
)

REM Get the device ID for betatester
echo.
echo Finding betatester device...
REM Try to find betatester specifically, otherwise use first device
for /f "tokens=1" %%i in ('adb devices -l ^| findstr /C:"betatester"') do (
    set DEVICE_ID=%%i
    goto found_device
)
REM If betatester not found by name, use first available device
for /f "tokens=1" %%i in ('adb devices ^| findstr /C:"device"') do (
    set DEVICE_ID=%%i
    goto found_device
)
echo Error: No device found!
exit /b 1
:found_device
echo Found device: !DEVICE_ID!

REM Build and install the app
echo.
echo Building and installing the app...
call gradlew.bat installDebug
if %errorlevel% neq 0 (
    echo Build failed!
    exit /b 1
)

REM Launch the app
echo.
echo Launching the app...
adb -s %DEVICE_ID% shell am start -n %ACTIVITY_NAME%
if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo App launched successfully!
    echo ========================================
) else (
    echo.
    echo Failed to launch app. Trying alternative method...
    adb -s %DEVICE_ID% shell monkey -p %PACKAGE_NAME% -c android.intent.category.LAUNCHER 1
)

endlocal

