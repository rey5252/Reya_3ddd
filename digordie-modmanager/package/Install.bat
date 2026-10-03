@echo off
rem Dig or Die Mod Manager - installer (runs Install.ps1 next to this file)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install.ps1" %*
echo.
pause
