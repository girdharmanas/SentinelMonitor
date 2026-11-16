@echo off
echo ========================================================
echo Starting Android Emulator (Pixel_9_Pro_XL)...
echo ========================================================
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Pixel_9_Pro_XL

echo Waiting for emulator to be ready...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" wait-for-device

:check_boot
for /f "tokens=*" %%a in ('"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell getprop sys.boot_completed 2^>nul') do set BOOT=%%a
if not "%BOOT%"=="1" (
    timeout /t 2 /nobreak >nul
    goto check_boot
)

echo System ready! Installing Sentinel Monitor...
for %%f in ("%~dp0artifacts\com.sentinel.monitor-*.apk") do (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r "%%f"
)
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell am start -n com.sentinel.monitor/.MainActivity
echo ========================================================
echo Sentinel Monitor is now running on your emulator!
echo ========================================================
pause
