@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem ==========================================================
rem  FortressTowers 1.0 — запуск RA3 з модом
rem  Використання:  launch_mod.bat ["шлях\до\FortressTowers_1.0.skudef"]
rem  Шлях до RA3.exe можна задати змінною середовища RA3_EXE.
rem ==========================================================

set "MOD_NAME=FortressTowers"
set "SKUDEF=%~1"
if "%SKUDEF%"=="" set "SKUDEF=%USERPROFILE%\Documents\Red Alert 3\Mods\%MOD_NAME%\%MOD_NAME%_1.0.skudef"
if not exist "%SKUDEF%" (
    if "%~1"=="" if exist "%APPDATA%\Red Alert 3\Mods\%MOD_NAME%\%MOD_NAME%_1.0.skudef" (
        set "SKUDEF=%APPDATA%\Red Alert 3\Mods\%MOD_NAME%\%MOD_NAME%_1.0.skudef"
    )
)
if not exist "%SKUDEF%" (
    echo [ПОМИЛКА] Не знайдено файл мода: "%SKUDEF%"
    echo Зберіть мод ^(scripts\build_mod.bat^) або передайте шлях до .skudef аргументом.
    exit /b 1
)

rem ---- Пошук RA3.exe -----------------------------------------
if defined RA3_EXE goto :have_exe
for %%K in ("HKLM\SOFTWARE\WOW6432Node\Electronic Arts\Electronic Arts\Red Alert 3" "HKLM\SOFTWARE\Electronic Arts\Electronic Arts\Red Alert 3") do (
    if not defined RA3_EXE (
        for /f "tokens=3*" %%A in ('reg query %%K /v "Install Dir" 2^>nul ^| find "REG_SZ"') do (
            if exist "%%B\RA3.exe" set "RA3_EXE=%%B\RA3.exe"
        )
    )
)
if defined RA3_EXE goto :have_exe
for %%P in (
    "%ProgramFiles(x86)%\Steam\steamapps\common\Command and Conquer Red Alert 3\RA3.exe"
    "%ProgramFiles(x86)%\Electronic Arts\Red Alert 3\RA3.exe"
    "%ProgramFiles(x86)%\Origin Games\Command and Conquer Red Alert 3\RA3.exe"
    "%ProgramFiles%\Electronic Arts\Red Alert 3\RA3.exe"
) do (
    if not defined RA3_EXE if exist %%P set "RA3_EXE=%%~P"
)
if not defined RA3_EXE (
    echo [ПОМИЛКА] Не знайдено RA3.exe. Задайте шлях:  set RA3_EXE=D:\Games\Red Alert 3\RA3.exe
    exit /b 1
)

:have_exe
echo Гра:  "%RA3_EXE%"
echo Мод:  "%SKUDEF%"
start "" "%RA3_EXE%" -modConfig "%SKUDEF%"
exit /b 0
