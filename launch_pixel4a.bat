@echo off
REM Launch Pixel 4a Android Emulator
REM This batch file launches the Pixel 4a emulator from Android SDK

set ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk
set EMULATOR_PATH=%ANDROID_SDK%\emulator\emulator.exe
set AVD_NAME=Pixel_4a

echo Starting Pixel 4a emulator...
echo Emulator path: %EMULATOR_PATH%
echo AVD name: %AVD_NAME%
echo.

if not exist "%EMULATOR_PATH%" (
    echo Error: Emulator not found at %EMULATOR_PATH%
    echo Please check your Android SDK installation.
    pause
    exit /b 1
)

"%EMULATOR_PATH%" -avd %AVD_NAME%

if errorlevel 1 (
    echo.
    echo Error: Failed to launch emulator
    pause
    exit /b 1
)

echo.
echo Emulator launched successfully!
