@echo off
setlocal
rem Phone lag check - Windows launcher. Needs adb (Android platform-tools).
rem Usage: double-click, or:  run.bat [check | watch SECONDS | full SECONDS]
cd /d "%~dp0"

where adb >nul 2>nul
if errorlevel 1 (
  echo adb was not found.
  echo Download "SDK Platform-Tools for Windows" from developer.android.com/tools/releases/platform-tools
  echo and either put adb.exe, AdbWinApi.dll and AdbWinUsbApi.dll in this folder, or add the folder to PATH.
  pause
  exit /b 1
)

set "MODE=%~1"
set "SECS=%~2"
if "%SECS%"=="" set "SECS=60"
if not "%MODE%"=="" goto run

echo.
echo   1) Quick check        (about 1 minute, just leave the phone)
echo   2) Watch while I use  (60 s recording - use the phone during it)
echo   3) Both
echo.
set /p CHOICE="Choose 1, 2 or 3 [1]: "
set "MODE=check"
if "%CHOICE%"=="2" set "MODE=watch"
if "%CHOICE%"=="3" set "MODE=full"

:run
echo.
echo Connected devices:
adb devices
adb get-state >nul 2>nul
if errorlevel 1 (
  echo No phone found ^(or more than one^). Check the cable / wireless debugging and accept the
  echo "Allow USB debugging?" prompt on the phone, then run this again. See README.md.
  pause
  exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%i"
set "OUT=report-%MODE%-%TS%.txt"

adb push lagcheck.sh /data/local/tmp/lagcheck.sh >nul
if errorlevel 1 ( echo Could not copy the script to the phone. & pause & exit /b 1 )

echo Running... progress shows below, the report is saved to %OUT%
adb shell sh /data/local/tmp/lagcheck.sh --%MODE% %SECS% > "%OUT%"
adb shell rm -f /data/local/tmp/lagcheck.sh

echo.
echo Report saved: %CD%\%OUT%
start "" notepad "%OUT%"
pause
