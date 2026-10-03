@echo off
rem Dig or Die Mod Manager - removes the Mod Manager (BepInEx and other mods stay)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install.ps1" -Uninstall %*
echo.
pause
