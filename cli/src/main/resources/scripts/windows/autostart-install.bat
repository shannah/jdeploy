@echo off
REM autostart-install.bat
REM Runs on Windows startup to install and launch the jDeploy app.
REM This file is placed in the Windows Startup folder via OEM customization.

echo Starting jDeploy installation...

REM Wait for shared folder to be available
:waitloop
if not exist "C:\shared\scripts\install-and-verify.ps1" (
    timeout /t 2 /nobreak >nul
    goto waitloop
)

REM Run the installation script
powershell -ExecutionPolicy Bypass -File "C:\shared\scripts\install-and-verify.ps1" -SharedDir "C:\shared"

REM Keep window open briefly to show completion
echo.
echo Installation complete!
timeout /t 5
