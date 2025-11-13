@echo off
REM Start Pixel 4a emulator if needed and install the debug build

setlocal
cd /d "%~dp0"

set ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk
set ADB_PATH=%ANDROID_SDK%\platform-tools\adb.exe
set EMULATOR_BAT=launch_pixel4a.bat

if not exist "%ADB_PATH%" (
    echo Error: adb not found at "%ADB_PATH%".
    echo Please ensure Android SDK Platform-Tools are installed.
    exit /b 1
)

echo Checking for running Android emulators...
set EMULATOR_RUNNING=
for /f "tokens=1" %%A in ('"%ADB_PATH%" devices ^| findstr /R "^emulator-[0-9][0-9]*"') do (
    set EMULATOR_RUNNING=%%A
)

if not defined EMULATOR_RUNNING (
    echo No running emulator detected. Launching Pixel 4a emulator...
    if not exist "%EMULATOR_BAT%" (
        echo Error: "%EMULATOR_BAT%" not found in %~dp0
        exit /b 1
    )
    start "" cmd /c ""%EMULATOR_BAT%""
    echo Waiting for emulator to connect...
    "%ADB_PATH%" wait-for-device
    echo Emulator connected. Waiting for Android to finish booting...
    :wait_boot
    for /f "usebackq tokens=1" %%B in (`"%ADB_PATH%" shell getprop sys.boot_completed 2^>nul`) do (
        if "%%B"=="1" goto boot_ready
    )
    timeout /t 2 >nul
    goto wait_boot
    :boot_ready
    echo Android boot completed.
) else (
    echo Emulator already running: %EMULATOR_RUNNING%
)

echo.
echo Installing Termux debug build...
call gradlew.bat app:installDebug
set GRADLE_EXIT=%ERRORLEVEL%

if %GRADLE_EXIT% neq 0 (
    echo Gradle build failed with exit code %GRADLE_EXIT%.
    exit /b %GRADLE_EXIT%
)

echo.
echo Termux debug build installed successfully.
exit /b 0

